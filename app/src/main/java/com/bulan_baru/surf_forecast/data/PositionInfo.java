package com.bulan_baru.surf_forecast.data;

import android.location.Location;

import com.google.gson.annotations.SerializedName;

import net.chetch.webservices.DataObject;

import java.util.Calendar;

public class PositionInfo extends DataObject{

    @Override
    public void initialise() {
        super.initialise();

        asDouble("latitude", "longitude");
    }

    public Calendar getFirstLight(){ return getCasted("first_light"); }
    public Calendar getLastLight(){ return getCasted("last_light"); }

    public double getLatitude(){
        return getCasted("latitude");
    }

    public double getLongitude(){
        return getCasted("longitude");
    }

    public Location getLocation(){
        Location l = new Location("");
        l.setLatitude(getLatitude());
        l.setLongitude(getLongitude());
        return l;
    }

}
