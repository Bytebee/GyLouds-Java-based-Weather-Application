package com.example.weatherapplication;

import static android.content.ContentValues.TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.UrlTileProvider;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SearchMenuFragment extends Fragment implements OnMapReadyCallback {

    private TextView textViewResult;
    private Button buttonSaveLocation;
    private MapView mapView;
    private GoogleMap googleMap;
    private TileOverlay tileOverlay;

    private double latitude;
    private double longitude;
    private String cityName;
    private String country;

    private String weatherData;
    private String hourlyForecastData;
    private String dailyForecastData;

    private final String OPEN_METEO_FORECAST_URL = "https://api.open-meteo.com/v1/forecast";

    public SearchMenuFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_menu, container, false);

        textViewResult = view.findViewById(R.id.textViewResult);
        buttonSaveLocation = view.findViewById(R.id.buttonSaveLocation);
        mapView = view.findViewById(R.id.mapView);

        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), getString(R.string.google_maps_key));
        }

        setupAutoCompleteFragment();

        buttonSaveLocation.setOnClickListener(v -> saveCurrentLocation());

        Bundle args = getArguments();
        if (args != null) {
            String location = args.getString("selectedLocation");
            if (location != null) {
                searchLocation(location);
            }
        }

        return view;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        this.googleMap = googleMap;

        updateMapLocation(latitude, longitude);
        addPrecipitationOverlay();
    }

    private void setupAutoCompleteFragment() {
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getChildFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        if (autocompleteFragment != null) {
            autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS_COMPONENTS));
            autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(Place place) {
                    cityName = place.getName();
                    latitude = place.getLatLng().latitude;
                    longitude = place.getLatLng().longitude;

                    country = null;
                    if (place.getAddressComponents() != null) {
                        for (com.google.android.libraries.places.api.model.AddressComponent component : place.getAddressComponents().asList()) {
                            if (component.getTypes().contains("country")) {
                                country = component.getName();
                                break;
                            }
                        }
                    }

                    updateMapLocation(latitude, longitude);

                    fetchWeatherData(latitude, longitude);
                    fetchHourlyForecast(latitude, longitude);
                    fetchDailyForecast(latitude, longitude);
                }

                @Override
                public void onError(com.google.android.gms.common.api.Status status) {
                    textViewResult.setText("Error: " + status.getStatusMessage());
                }
            });

        } else {
            Log.e("SearchMenuFragment", "Autocomplete fragment is null");
        }
    }

    private void updateMapLocation(double latitude, double longitude) {
        if (googleMap != null) {
            LatLng location = new LatLng(latitude, longitude);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 10));
            Log.d(TAG, "Map centered to: " + latitude + ", " + longitude);
        }
    }

    private void addPrecipitationOverlay() {
        UrlTileProvider tileProvider = new UrlTileProvider(256, 256) {
            @Override
            public URL getTileUrl(int x, int y, int zoom) {
                String urlString = String.format(Locale.US,
                        "https://maps.open-meteo.com/v1/forecast/%d/%d/%d.png",
                        zoom, x, y);
                Log.d(TAG, "Fetching tile with URL: " + urlString);
                try {
                    return new URL(urlString);
                } catch (MalformedURLException e) {
                    Log.e(TAG, "Malformed Open-Meteo tile URL", e);
                    return null;
                }
            }
        };

        if (googleMap != null) {
            TileOverlayOptions tileOverlayOptions = new TileOverlayOptions().tileProvider(tileProvider);
            tileOverlay = googleMap.addTileOverlay(tileOverlayOptions);
            Log.d(TAG, "Precipitation overlay added to the map");
        } else {
            Log.e(TAG, "Error adding precipitation overlay: GoogleMap is null");
        }
    }

    private void saveCurrentLocation() {
        if (cityName != null && country != null) {
            String location = cityName + ", " + country;
            SharedPreferences prefs = getActivity().getSharedPreferences("SavedLocationsPrefs", Context.MODE_PRIVATE);
            Set<String> currentLocations = prefs.getStringSet("locations", new HashSet<>());
            if (currentLocations == null) {
                Log.d("SaveLocation", "Current locations set is null, creating new set.");
                currentLocations = new HashSet<>();
            }
            Set<String> newLocations = new HashSet<>(currentLocations);
            newLocations.add(location);

            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet("locations", newLocations);
            editor.apply();

            Log.d("SaveLocation", "Location saved: " + location);
            Log.d("SaveLocation", "All saved locations: " + newLocations.toString());

            Toast.makeText(getActivity(), "Location saved: " + location, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getActivity(), "No location data to save.", Toast.LENGTH_SHORT).show();
            Log.d("SaveLocation", "No location data provided to save.");
        }
    }

    private void saveLocationsToFile(Set<String> locations) {
        StringBuilder data = new StringBuilder();
        for (String location : locations) {
            data.append(location).append("\n");
        }

        try (FileOutputStream fos = requireActivity().openFileOutput("saved_locations.txt", Context.MODE_PRIVATE)) {
            ((FileOutputStream) fos).write(data.toString().getBytes());
            Log.d("SearchMenuFragment", "Locations saved to file.");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("SearchMenuFragment", "Error saving locations to file.", e);
        }
    }

    private void navigateToSearchMenu(String location) {
        Bundle bundle = new Bundle();
        if (location != null) {
            bundle.putString("selectedLocation", location);
        }

        try {
            NavController navController = NavHostFragment.findNavController(this);
            navController.navigate(R.id.menu_search, bundle);
        } catch (Exception e) {
            Log.e("SearchMenuFragment", "Error navigating to search menu: " + e.getMessage());
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) requireActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void fetchWeatherData(double latitude, double longitude) {
        if (!isNetworkAvailable()) {
            textViewResult.setText("No internet connection.");
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
                requireActivity().runOnUiThread(() -> textViewResult.append("Failed to load weather data.\n"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    weatherData = response.body().string();
                    checkAndDisplayData();
                } else {
                    Log.e(TAG, "Weather data request failed with code: " + response.code());
                    requireActivity().runOnUiThread(() -> textViewResult.append("Weather data not available.\n"));
                }
            }
        });
    }

    private void fetchHourlyForecast(double latitude, double longitude) {
        if (!isNetworkAvailable()) {
            textViewResult.setText("No internet connection.");
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
                requireActivity().runOnUiThread(() -> textViewResult.append("Failed to load hourly forecast.\n"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    hourlyForecastData = response.body().string();
                    checkAndDisplayData();
                } else {
                    Log.e(TAG, "Hourly forecast request failed with code: " + response.code());
                    requireActivity().runOnUiThread(() -> textViewResult.append("Hourly forecast data not available.\n"));
                }
            }
        });
    }

    private void fetchDailyForecast(double latitude, double longitude) {
        if (!isNetworkAvailable()) {
            textViewResult.setText("No internet connection.");
            return;
        }

        OkHttpClient client = new OkHttpClient();

        String url = OPEN_METEO_FORECAST_URL + "?latitude=" + latitude + "&longitude=" + longitude + "&daily=temperature_2m_max,temperature_2m_min,weathercode&temperature_unit=celsius";

        Request request = new Request.Builder().url(url).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch daily forecast: " + e.getMessage());
                requireActivity().runOnUiThread(() -> textViewResult.append("Failed to load daily forecast.\n"));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    dailyForecastData = response.body().string();
                    checkAndDisplayData();
                } else {
                    Log.e(TAG, "Daily forecast request failed with code: " + response.code());
                    requireActivity().runOnUiThread(() -> textViewResult.append("Daily forecast data not available.\n"));
                }
            }
        });
    }

    private String parseWeatherAndAirQualityJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject currentWeather = jsonObject.getJSONObject("current_weather");

            double temperature = currentWeather.getDouble("temperature");
            int weatherCode = currentWeather.getInt("weathercode");
            double windSpeed = currentWeather.optDouble("windspeed", 0.0);
            double windDirection = currentWeather.optDouble("winddirection", 0.0);

            String weatherDescription = getWeatherDescription(weatherCode);

            return String.format(Locale.getDefault(),
                    "Temperature: %.1f°C\nWeather: %s\nWind Speed: %.2f m/s\nWind Direction: %.0f°\n",
                    temperature, weatherDescription, windSpeed, windDirection);
        } catch (JSONException e) {
            Log.e("SearchMenuFragment", "Error parsing weather JSON: " + e.getMessage());
            return "Error parsing weather data.";
        }
    }

    private void checkAndDisplayData() {
        if (weatherData != null && hourlyForecastData != null && dailyForecastData != null) {
            requireActivity().runOnUiThread(() -> {

                try {
                    JSONObject jsonObject = new JSONObject(dailyForecastData);
                    JSONArray dailyArray = jsonObject.getJSONObject("daily").getJSONArray("time");
                    JSONObject firstDayForecast = jsonObject.getJSONObject("daily");

                    double minTemp = firstDayForecast.getJSONArray("temperature_2m_min").getDouble(0);
                    double maxTemp = firstDayForecast.getJSONArray("temperature_2m_max").getDouble(0);

                    String locationDisplay = cityName + ", " + country;
                    String temperature = parseTemperatureFromWeatherData(weatherData);
                    String description = parseDescriptionFromWeatherData(weatherData);
                    String highLowTemp = String.format(Locale.getDefault(), "High: %.1f°C | Low: %.1f°C", maxTemp, minTemp);

                    textViewResult.setText(String.format("%s\n%s\n%s\n%s", locationDisplay, temperature, description, highLowTemp));

                    StringBuilder displayContent = new StringBuilder();
                    displayContent.append(parseHourlyForecastJson(hourlyForecastData))
                            .append("\n")
                            .append(parseDailyForecastJson(dailyForecastData));

                    textViewResult.append("\n" + displayContent.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing daily forecast data: " + e.getMessage());
                    textViewResult.setText("Error parsing daily forecast data.");
                }
            });
        }
    }

    private String parseTemperatureFromWeatherData(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject currentWeather = jsonObject.getJSONObject("current_weather");
            return String.format(Locale.getDefault(), "%.1f°C", currentWeather.getDouble("temperature"));
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing temperature from JSON: " + e.getMessage());
            return "N/A";
        }
    }

    private String parseDescriptionFromWeatherData(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject currentWeather = jsonObject.getJSONObject("current_weather");
            int weatherCode = currentWeather.getInt("weathercode");
            return getWeatherDescription(weatherCode);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing description from JSON: " + e.getMessage());
            return "N/A";
        }
    }

    private String parseHourlyForecastJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONObject hourly = jsonObject.getJSONObject("hourly");
            JSONArray temperatureArray = hourly.optJSONArray("temperature_2m");
            JSONArray weathercodeArray = hourly.optJSONArray("weathercode");
            JSONArray timeArray = hourly.optJSONArray("time");

            if (temperatureArray == null || weathercodeArray == null || timeArray == null ||
                    temperatureArray.length() != timeArray.length() || weathercodeArray.length() != timeArray.length()) {
                Log.e("SearchMenuFragment", "Error: JSON arrays are missing or not of equal length");
                return "Error parsing hourly forecast.\n";
            }

            SimpleDateFormat localFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault());
            localFormat.setTimeZone(TimeZone.getDefault());

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
                    int weatherCode = weathercodeArray.getInt(i);
                    String weatherDescription = getWeatherDescription(weatherCode);

                    hourlyForecast.append(String.format(Locale.getDefault(), "%s - %.1f°C, %s\n",
                            formattedTime, temperature, weatherDescription));

                    count++;
                    if (count >= 5) break;
                }
            }
            return hourlyForecast.toString();
        } catch (JSONException | ParseException e) {
            Log.e("SearchMenuFragment", "Error parsing hourly forecast JSON: " + e.getMessage());
            return "Error parsing hourly forecast.\n";
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
        } catch (JSONException | ParseException e) {
            Log.e("SearchMenuFragment", "Error parsing daily forecast JSON: " + e.getMessage());
            return "Error parsing daily forecast.\n";
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

    private void searchLocation(String location) {
        PlacesClient placesClient = Places.createClient(requireActivity());
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setQuery(location)
                .build();

        placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
            if (!response.getAutocompletePredictions().isEmpty()) {
                AutocompletePrediction prediction = response.getAutocompletePredictions().get(0);
                fetchPlace(prediction.getPlaceId());
            } else {
                textViewResult.setText("No predictions found for the location.");
            }
        }).addOnFailureListener(exception -> {
            Log.e(TAG, "Error fetching place predictions: " + exception.getMessage());
            textViewResult.setText("Error fetching place predictions.");
        });
    }

    private void fetchPlace(String placeId) {
        PlacesClient placesClient = Places.createClient(requireActivity());
        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, Arrays.asList(Place.Field.LAT_LNG, Place.Field.NAME, Place.Field.ADDRESS_COMPONENTS)).build();

        placesClient.fetchPlace(request).addOnSuccessListener(response -> {
            Place place = response.getPlace();
            cityName = place.getName();
            latitude = place.getLatLng().latitude;
            longitude = place.getLatLng().longitude;

            country = null;
            if (place.getAddressComponents() != null) {
                for (com.google.android.libraries.places.api.model.AddressComponent component : place.getAddressComponents().asList()) {
                    if (component.getTypes().contains("country")) {
                        country = component.getName();
                        break;
                    }
                }
            }

            updateMapLocation(latitude, longitude);

            fetchWeatherData(latitude, longitude);
            fetchHourlyForecast(latitude, longitude);
            fetchDailyForecast(latitude, longitude);
        }).addOnFailureListener(exception -> {
            Log.e(TAG, "Error fetching place details: " + exception.getMessage());
            textViewResult.setText("Error fetching place details.");
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mapView != null) {
            mapView.onDestroy();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }
}
