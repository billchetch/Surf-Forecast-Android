package com.bulan_baru.surf_forecast;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bulan_baru.surf_forecast.data.Location;
import com.bulan_baru.surf_forecast.data.Locations;

import net.chetch.appframework.IDialogManager;
import net.chetch.utilities.SLog;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class LocationsAdapter extends RecyclerView.Adapter<LocationsAdapter.ViewHolder>{

    public interface LocationsAdapaterListener{
        void onSelectLocation(Location location);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        View contentView;
        LocationFragment locationFragment;

        public ViewHolder(View v, LocationFragment locationFragment) {
            super(v);
            contentView = v;
            this.locationFragment = locationFragment;
        }


    }


    public Locations locations;
    LocationsAdapaterListener listener;

    public void setLocations(Locations locations){
        this.locations = locations;
        //notifyDataSetChanged();
    }

    private void selectLocation(Location location){
        if(listener != null)listener.onSelectLocation(location);
    }

    public void setListener(LocationsAdapaterListener listener){
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // create a new view
        LocationFragment lf = new LocationFragment();
        View v = lf.onCreateView(LayoutInflater.from(parent.getContext()), parent, null);
        v.setOnClickListener( view -> {
                    selectLocation(lf.location);
                }
        );
        ViewHolder vh = new ViewHolder(v, lf);
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if(locations == null || locations.size() == 0)return;

        Location location = locations.get(position);
        holder.locationFragment.populateContent(location);

        if(SLog.LOG)SLog.i("LA", "Binding view holder @ position " + position);
    }

    @Override
    public int getItemCount() {
        return locations == null ? 0 : locations.size();
    }
}
