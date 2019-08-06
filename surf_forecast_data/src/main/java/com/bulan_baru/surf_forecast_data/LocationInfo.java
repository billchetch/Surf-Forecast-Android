package com.bulan_baru.surf_forecast_data;

import android.location.Location;

import com.google.gson.annotations.SerializedName;

import java.util.Calendar;
import java.util.TimeZone;

public class LocationInfo {

    private double latitude = 0;
    private double longitude = 0;

    @SerializedName("first_light")
    private Calendar firstLight;
    @SerializedName("last_light")
    private Calendar lastLight;

    public Calendar getFirstLight(){ return firstLight; }
    public Calendar getLastLight(){ return lastLight; }

    public Location getLocation(){
        Location l = new Location("");
        l.setLatitude(latitude);
        l.setLongitude(longitude);
        return l;
    }

}
