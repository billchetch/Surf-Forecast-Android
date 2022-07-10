package com.bulan_baru.surf_forecast;

import android.app.Dialog;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.bulan_baru.surf_forecast.data.Location;
import com.bulan_baru.surf_forecast.data.Locations;

import net.chetch.appframework.GenericDialogFragment;


public class LocationsDialogFragment extends GenericDialogFragment implements LocationsAdapter.LocationsAdapaterListener {
    LocationsAdapter locationsAdapater;
    public Locations locations;
    public Location location;

    LocationsDialogFragment(){
        //default
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        inflateContentView(R.layout.locations_dialog);

        RecyclerView locationsRecyclerView = contentView.findViewById(R.id.locationsRecyclerView);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        //locationsRecyclerView.setHasFixedSize(true);
        locationsRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        locationsAdapater = new LocationsAdapter();
        locationsAdapater.setLocations(locations);
        locationsAdapater.setListener(this);
        locationsRecyclerView.setAdapter(locationsAdapater);

        // Create the AlertDialog object and return it
        return createDialog();
    }

    @Override
    public void onSelectLocation(Location location) {
        this.location = location;
        dialogManager.onDialogPositiveClick(this);
        dismiss();
    }
}
