package com.bulan_baru.surf_forecast;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bulan_baru.surf_forecast.data.Location;

import androidx.fragment.app.Fragment;

public class LocationFragment extends Fragment {

    private View contentView;
    public Location location;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        contentView = inflater.inflate(R.layout.location_fragment, container, false);


        return contentView;
    }

    public void populateContent(Location location){
        this.location = location;
        TextView tv = contentView.findViewById(R.id.locationTitle);

        tv.setText(location.getLocationAndDistance());

        tv = contentView.findViewById(R.id.locationDescription);
        String desc = location.getDescription();
        if(desc == null || desc.isEmpty()){
            tv.setVisibility(View.GONE);
        } else {
            tv.setText(location.getDescription());
            tv.setVisibility(View.VISIBLE);
        }
    }
}
