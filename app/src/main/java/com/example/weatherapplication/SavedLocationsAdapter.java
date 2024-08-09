package com.example.weatherapplication;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SavedLocationsAdapter extends RecyclerView.Adapter<SavedLocationsAdapter.LocationViewHolder> {

    private List<String> locations;
    private Context context;
    private OnLocationActionListener actionListener;

    public interface OnLocationActionListener {
        void onLocationClick(String location);
        void onLocationDelete(int position, String location);
    }

    public SavedLocationsAdapter(Context context, List<String> locations, OnLocationActionListener listener) {
        this.context = context;
        this.locations = locations;
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public LocationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_location, parent, false);
        return new LocationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocationViewHolder holder, int position) {
        String location = locations.get(position);
        holder.locationName.setText(location);
        holder.itemView.setOnClickListener(v -> actionListener.onLocationClick(location));

        holder.deleteButton.setOnClickListener(v -> {
            actionListener.onLocationDelete(position, location);
        });
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    public void removeLocation(int position) {
        locations.remove(position);
        notifyItemRemoved(position);
    }

    static class LocationViewHolder extends RecyclerView.ViewHolder {
        TextView locationName;
        Button deleteButton;

        public LocationViewHolder(@NonNull View itemView) {
            super(itemView);
            locationName = itemView.findViewById(R.id.textViewLocation);
            deleteButton = itemView.findViewById(R.id.buttonDelete);
        }
    }
}
