package com.example.weatherapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SavedLocationsFragment extends Fragment implements SavedLocationsAdapter.OnLocationActionListener {

    private RecyclerView recyclerViewSavedLocations;
    private SavedLocationsAdapter adapter;
    private List<String> locationsList;
    private final String PREFS_NAME = "SavedLocationsPrefs";
    private final String PREFS_LOCATIONS_KEY = "locations";
    private final String FILE_NAME = "saved_locations.txt";

    public SavedLocationsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_saved_locations, container, false);

        recyclerViewSavedLocations = view.findViewById(R.id.recyclerViewSavedLocations);
        recyclerViewSavedLocations.setLayoutManager(new LinearLayoutManager(getContext()));

        loadSavedLocations();

        return view;
    }

    private void loadSavedLocations() {
        locationsList = readLocationsFromFile();
        if (locationsList == null) {
            locationsList = new ArrayList<>();
        }

        adapter = new SavedLocationsAdapter(requireContext(), locationsList, this);
        recyclerViewSavedLocations.setAdapter(adapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                adapter.notifyItemChanged(position);

                View itemView = viewHolder.itemView;
                Button deleteButton = itemView.findViewById(R.id.buttonDelete);
                ((View) deleteButton).setVisibility(View.VISIBLE);
                deleteButton.setOnClickListener(v -> {
                    String location = locationsList.get(position);
                    deleteLocation(position, location);
                    deleteButton.setVisibility(View.INVISIBLE);
                });
            }
        });

        itemTouchHelper.attachToRecyclerView(recyclerViewSavedLocations);
    }

    private void deleteLocation(int position, String location) {
        adapter.removeLocation(position);
        deleteLocationFromStorage(location);
    }

    private void deleteLocationFromStorage(String location) {
        SharedPreferences prefs = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> savedLocations = prefs.getStringSet(PREFS_LOCATIONS_KEY, new HashSet<>());
        if (savedLocations != null) {
            savedLocations.remove(location);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(PREFS_LOCATIONS_KEY, savedLocations);
            editor.apply();
        }

        List<String> updatedLocations = readLocationsFromFile();
        if (updatedLocations != null) {
            updatedLocations.remove(location);
            writeLocationsToFile(updatedLocations);
        }

        Log.d("SavedLocationsFragment", "Location deleted from storage: " + location);
    }

    private List<String> readLocationsFromFile() {
        List<String> locations = new ArrayList<>();
        try (FileInputStream fis = requireActivity().openFileInput(FILE_NAME)) {
            byte[] buffer = new byte[fis.available()];
            fis.read(buffer);
            String data = new String(buffer);
            String[] locationArray = data.split("\n");
            for (String location : locationArray) {
                if (!location.trim().isEmpty()) {
                    locations.add(location);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("SavedLocationsFragment", "Error reading locations from file.", e);
        }
        return locations;
    }

    private void writeLocationsToFile(List<String> locations) {
        StringBuilder data = new StringBuilder();
        for (String location : locations) {
            data.append(location).append("\n");
        }
        try (FileOutputStream fos = requireActivity().openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
            fos.write(data.toString().getBytes());
            Log.d("SavedLocationsFragment", "Locations saved to file.");
        } catch (IOException e) {
            Log.e("SavedLocationsFragment", "Error saving locations to file: " + e.getMessage(), e);
        }
    }

    @Override
    public void onLocationClick(String location) {
        navigateToSearchMenu(location);
    }

    @Override
    public void onLocationDelete(int position, String location) {
        deleteLocation(position, location);
    }

    private void navigateToSearchMenu(String location) {
        Bundle bundle = new Bundle();
        bundle.putString("selectedLocation", location);

        try {
            NavController navController = NavHostFragment.findNavController(this);
            navController.navigate(R.id.menu_search, bundle);
        } catch (Exception e) {
            Log.e("SavedLocationsFragment", "Error navigating to search menu: " + e.getMessage());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadSavedLocations();
    }

}
