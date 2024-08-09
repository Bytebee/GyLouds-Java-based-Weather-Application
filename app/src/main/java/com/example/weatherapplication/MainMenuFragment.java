package com.example.weatherapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.UrlTileProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainMenuFragment extends Fragment implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "MainMenuFragment";

    private TextView textViewLocation, textViewTemperature, textViewDescription, textViewDetailedInfo, textViewResult;
    private double latitude = 0.0;
    private double longitude = 0.0;
    private String locationName = "Unknown Location";

    private String weatherData;
    private String hourlyForecastData;
    private String dailyForecastData;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private MapView mapView;
    private GoogleMap googleMap;

    private final String OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    public MainMenuFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main_menu, container, false);

        textViewLocation = view.findViewById(R.id.textViewLocation);
        textViewTemperature = view.findViewById(R.id.textViewTemperature);
        textViewDescription = view.findViewById(R.id.textViewDescription);
        textViewDetailedInfo = view.findViewById(R.id.textViewDetailedInfo);
        textViewResult = view.findViewById(R.id.textViewResult);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        mapView = view.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        initLocationServices();
        checkPermissionsAndGetLocation();
        return view;
    }

    private void initLocationServices() {
        locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000)
                .setFastestInterval(5000);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (android.location.Location location : locationResult.getLocations()) {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    getLocationName(latitude, longitude);
                    fetchWeatherData(latitude, longitude);
                    fetchHourlyForecast(latitude, longitude);
                    fetchDailyForecast(latitude, longitude);
                    updateMapLocation(latitude, longitude);
                }
            }
        };
    }

    private void checkAndDisplayData() {
        if (weatherData != null && hourlyForecastData != null && dailyForecastData != null) {
            requireActivity().runOnUiThread(() -> {

                textViewLocation.setText(locationName);
                textViewTemperature.setText(parseTemperatureFromWeatherData(weatherData));
                textViewDescription.setText(parseDescriptionFromWeatherData(weatherData));

                String description = parseDescriptionFromWeatherData(weatherData);
                String minMaxTemps = parseDailyForecastMinAndMax(dailyForecastData);
                textViewDescription.setText(String.format("%s\n%s", description, minMaxTemps));

                StringBuilder detailedInfo = new StringBuilder();
                detailedInfo.append(parseWeatherJson(weatherData))
                        .append(parseHourlyForecastJson(hourlyForecastData))
                        .append(parseDailyForecastJson(dailyForecastData));
                textViewDetailedInfo.setText(detailedInfo.toString());
            });
        }
    }

    private String parseTemperatureFromWeatherData(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject currentWeather = jsonObject.getJSONObject("current_weather");
            double temperature = currentWeather.getDouble("temperature");
            return String.format(Locale.getDefault(), "%.1f°C", temperature);
        } catch (Exception e) {
            Log.e("MainMenuFragment", "Error parsing temperature from JSON: " + e.getMessage());
            return "N/A";
        }
    }

    private String parseDescriptionFromWeatherData(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject currentWeather = jsonObject.getJSONObject("current_weather");
            int weatherCode = currentWeather.getInt("weathercode");
            String weatherDescription = getWeatherDescription(weatherCode);
            return weatherDescription;
        } catch (Exception e) {
            Log.e("MainMenuFragment", "Error parsing description from JSON: " + e.getMessage());
            return "N/A";
        }
    }

    private String getWeatherDescription(int weatherCode) {
        switch (weatherCode) {
            case 0:
                return "Clear sky";
            case 1:
            case 2:
            case 3:
                return "Partly cloudy";
            case 45:
            case 48:
                return "Fog";
            case 51:
            case 53:
            case 55:
                return "Drizzle";
            case 61:
            case 63:
            case 65:
                return "Rain";
            case 71:
            case 73:
            case 75:
                return "Snow";
            case 80:
            case 81:
            case 82:
                return "Rain showers";
            case 95:
                return "Thunderstorm";
            case 96:
            case 99:
                return "Thunderstorm with hail";
            default:
                return "Unknown";
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        updateMapLocation(latitude, longitude);

        addWeatherMapOverlay();
    }

    private void updateMapLocation(double latitude, double longitude) {
        if (googleMap != null) {
            LatLng location = new LatLng(latitude, longitude);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 10));
        }
    }

    private void addWeatherMapOverlay() {
        UrlTileProvider tileProvider = new UrlTileProvider(256, 256) {
            @Nullable
            @Override
            public URL getTileUrl(int x, int y, int zoom) {
                String urlString = String.format(Locale.US, "https://maps.open-meteo.com/v1/forecast/%d/%d/%d.png", zoom, x, y);
                try {
                    URL url = new URL(urlString);
                    Log.d(TAG, "Fetching weather tile from: " + url);
                    return url;
                } catch (MalformedURLException e) {
                    Log.e(TAG, "Malformed tile URL", e);
                    return null;
                }
            }
        };

        if (tileProvider != null && googleMap != null) {
            TileOverlayOptions tileOverlayOptions = new TileOverlayOptions().tileProvider(tileProvider);
            googleMap.addTileOverlay(tileOverlayOptions);
            Log.d(TAG, "Tile overlay added to the map");
        } else {
            Log.e(TAG, "Error adding tile overlay: TileProvider or GoogleMap is null");
        }
    }

    private void checkPermissionsAndGetLocation() {
        if (ContextCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                        Log.i(TAG, "Current location: " + latitude + ", " + longitude);

                        getLocationName(latitude, longitude);
                        fetchWeatherData(latitude, longitude);
                        fetchHourlyForecast(latitude, longitude);
                        fetchDailyForecast(latitude, longitude);
                        updateMapLocation(latitude, longitude);
                    } else {
                        requestLocationUpdates();
                    }
                });
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        if (textViewResult != null) {
            requireActivity().runOnUiThread(() -> textViewResult.setText("Requesting location updates..."));
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void getLocationName(double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                locationName = address.getLocality();
                if (locationName == null) {
                    locationName = address.getSubAdminArea();
                }
                if (locationName == null) {
                    locationName = "Unknown Location";
                }
                Log.d(TAG, "Resolved location name: " + locationName);
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoder failed to get location name", e);
        }
    }

    private void fetchWeatherData(double latitude, double longitude) {
        if (!isNetworkAvailable()) {
            updateTextView(textViewResult, "No internet connection.\n");
            return;
        }

        OkHttpClient client = new OkHttpClient();
        String url = OPEN_METEO_FORECAST_URL + "?latitude=" + latitude + "&longitude=" + longitude +
                "&current_weather=true&hourly=temperature_2m,apparent_temperature,weathercode,windspeed_10m,winddirection_10m&temperature_unit=celsius";

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch weather data: " + e.getMessage());
                updateTextView(textViewResult, "Failed to load weather data.\n");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    Log.d(TAG, "Weather data received: " + responseData);
                    weatherData = responseData;
                    checkAndDisplayData();
                } else {
                    Log.e(TAG, "Weather data request failed with code: " + response.code());
                    updateTextView(textViewResult, "Weather data not available.\n");
                }
            }
        });
    }

    private void updateTextView(TextView textView, String text) {
        requireActivity().runOnUiThread(() -> textView.append(text));
    }

    private void fetchDailyForecast(double latitude, double longitude) {
        if (!isNetworkAvailable()) {
            if (textViewResult != null) {
                requireActivity().runOnUiThread(() -> textViewResult.append("No internet connection.\n"));
            }
            return;
        }

        OkHttpClient client = new OkHttpClient();
        String url = OPEN_METEO_FORECAST_URL + "?latitude=" + latitude + "&longitude=" + longitude + "&daily=temperature_2m_max,temperature_2m_min,weathercode&temperature_unit=celsius";

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch daily forecast: " + e.getMessage());
                if (textViewResult != null) {
                    requireActivity().runOnUiThread(() -> textViewResult.append("Failed to load daily forecast.\n"));
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    dailyForecastData = response.body().string();
                    Log.d(TAG, "Daily forecast data received: " + dailyForecastData);
                    checkAndDisplayData();
                } else {
                    Log.e(TAG, "Daily forecast request failed with code: " + response.code());
                    if (textViewResult != null) {
                        requireActivity().runOnUiThread(() -> textViewResult.append("Daily forecast data not available.\n"));
                    }
                }
            }
        });
    }

    private void fetchHourlyForecast(double latitude, double longitude) {
        if (!isNetworkAvailable()) {
            updateTextView(textViewResult, "No internet connection.\n");
            return;
        }

        OkHttpClient client = new OkHttpClient();
        String url = OPEN_METEO_FORECAST_URL + "?latitude=" + latitude + "&longitude=" + longitude +
                "&hourly=temperature_2m,apparent_temperature,weathercode,windspeed_10m,winddirection_10m&timezone=auto";

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch hourly forecast: " + e.getMessage());
                updateTextView(textViewResult, "Failed to load hourly forecast.\n");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    hourlyForecastData = response.body().string();
                    Log.d(TAG, "Hourly forecast data received: " + hourlyForecastData);
                    checkAndDisplayData();
                } else {
                    Log.e(TAG, "Hourly forecast request failed with code: " + response.code());
                    updateTextView(textViewResult, "Hourly forecast data not available.\n");
                }
            }
        });
    }

    private String parseHourlyForecastJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject hourly = jsonObject.getJSONObject("hourly");
            JSONArray temperatureArray = hourly.optJSONArray("temperature_2m");
            JSONArray apparentTemperatureArray = hourly.optJSONArray("apparent_temperature");
            JSONArray weathercodeArray = hourly.optJSONArray("weathercode");
            JSONArray timeArray = hourly.optJSONArray("time");

            if (temperatureArray == null || apparentTemperatureArray == null || weathercodeArray == null || timeArray == null ||
                    temperatureArray.length() != timeArray.length() || apparentTemperatureArray.length() != timeArray.length() || weathercodeArray.length() != timeArray.length()) {
                Log.e("MainMenuFragment", "Error: JSON arrays are missing or not of equal length");
                return "Error parsing hourly forecast.\n";
            }

            SimpleDateFormat localFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault());

            StringBuilder hourlyForecast = new StringBuilder("Hourly Forecast:\n");
            Calendar currentCal = Calendar.getInstance();
            int currentHour = currentCal.get(Calendar.HOUR_OF_DAY);
            int currentDay = currentCal.get(Calendar.DAY_OF_YEAR);
            int count = 0;

            for (int i = 0; i < timeArray.length() && count < 5; i++) {
                Date forecastDate = localFormat.parse(timeArray.getString(i));
                Calendar forecastCal = Calendar.getInstance();
                forecastCal.setTime(forecastDate);

                if ((forecastCal.get(Calendar.DAY_OF_YEAR) == currentDay && forecastCal.get(Calendar.HOUR_OF_DAY) >= currentHour) ||
                        forecastCal.get(Calendar.DAY_OF_YEAR) > currentDay) {
                    String formattedTime = String.format(Locale.getDefault(), "%d:00 %s",
                            forecastCal.get(Calendar.HOUR) == 0 ? 12 : forecastCal.get(Calendar.HOUR),
                            forecastCal.get(Calendar.AM_PM) == Calendar.AM ? "AM" : "PM");

                    double temperature = temperatureArray.getDouble(i);
                    double feelsLike = apparentTemperatureArray.getDouble(i);
                    int weatherCode = weathercodeArray.getInt(i);
                    String weatherDescription = getWeatherDescription(weatherCode);

                    hourlyForecast.append(String.format(Locale.getDefault(), "%s - %.1f°C (Feels like %.1f°C), %s\n",
                            formattedTime, temperature, feelsLike, weatherDescription));

                    count++;
                    if (count >= 5) break;
                }
            }
            return hourlyForecast.toString();
        } catch (JSONException | ParseException e) {
            Log.e("MainMenuFragment", "Error parsing hourly forecast JSON: " + e.getMessage(), e);
            return "Error parsing hourly forecast.\n";
        }
    }

    private String parseWeatherJson(String json) {
        try {
            Log.d("MainMenuFragment", "Received current weather JSON: " + json);

            JSONObject jsonObject = new JSONObject(json);
            if (!jsonObject.has("current_weather") || !jsonObject.has("hourly")) {
                Log.e("MainMenuFragment", "Missing required data objects in the response.");
                return "Weather data is incomplete.\n";
            }

            JSONObject currentWeather = jsonObject.getJSONObject("current_weather");
            JSONObject hourly = jsonObject.getJSONObject("hourly");
            JSONArray apparentTemperatureArray = hourly.getJSONArray("apparent_temperature");

            double temperature = currentWeather.getDouble("temperature");
            double feelsLike = apparentTemperatureArray.getDouble(0);
            double windSpeed = currentWeather.optDouble("windspeed", 0.0);
            double windDirection = currentWeather.optDouble("winddirection", 0.0);
            int weatherCode = currentWeather.optInt("weathercode", -1);
            double visibility = currentWeather.optDouble("visibility", 0.0);

            String weatherDescription = getWeatherDescription(weatherCode);

            return String.format(Locale.getDefault(),
                    "Temperature: %.1f°C\nFeels Like: %.1f°C\nWeather: %s\n" +
                            "Wind Speed: %.2f m/s\nWind Direction: %.0f°\nVisibility: %.1f km\n",
                    temperature, feelsLike, weatherDescription, windSpeed, windDirection, visibility);
        } catch (Exception e) {
            Log.e("MainMenuFragment", "Error parsing weather JSON: " + e.getMessage(), e);
            Log.e("MainMenuFragment", "Response JSON: " + json);
            return "Error parsing weather data.\n";
        }
    }

    private String parseDailyForecastJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject daily = jsonObject.getJSONObject("daily");
            JSONArray maxTempArray = daily.getJSONArray("temperature_2m_max");
            JSONArray minTempArray = daily.getJSONArray("temperature_2m_min");
            JSONArray weathercodeArray = daily.getJSONArray("weathercode");
            JSONArray dateArray = daily.getJSONArray("time");

            StringBuilder dailyForecast = new StringBuilder("Daily Forecast:\n");
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("MM/dd", Locale.getDefault());

            for (int i = 0; i < dateArray.length(); i++) {
                String date = dateArray.getString(i);
                Date parsedDate = inputFormat.parse(date);
                String formattedDate = outputFormat.format(parsedDate);

                double lowTemp = minTempArray.getDouble(i);
                double highTemp = maxTempArray.getDouble(i);
                int weatherCode = weathercodeArray.getInt(i);
                String weatherDescription = getWeatherDescription(weatherCode);
                dailyForecast.append(String.format(Locale.getDefault(), "%s - Low: %.1f°C, High: %.1f°C, %s\n", formattedDate, lowTemp, highTemp, weatherDescription));
            }
            return dailyForecast.toString();
        } catch (Exception e) {
            Log.e("MainMenuFragment", "Error parsing daily forecast JSON: " + e.getMessage());
            return "Error parsing daily forecast.\n";
        }
    }

    private String parseDailyForecastMinAndMax(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject daily = jsonObject.getJSONObject("daily");
            JSONArray maxTempArray = daily.getJSONArray("temperature_2m_max");
            JSONArray minTempArray = daily.getJSONArray("temperature_2m_min");

            if (maxTempArray.length() > 0) {
                double minTemp = minTempArray.getDouble(0);
                double maxTemp = maxTempArray.getDouble(0);
                return String.format(Locale.getDefault(), "High: %.1f°C | Low: %.1f°C", maxTemp, minTemp);
            }
        } catch (Exception e) {
            Log.e("MainMenuFragment", "Error parsing min and max temperatures from daily forecast JSON: " + e.getMessage());
        }
        return "High: N/A Low: N/A";
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}
