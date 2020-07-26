package com.bulan_baru.surf_forecast.data;

import net.chetch.webservices.DataObject;

public class Location extends DataObject {
    public static final int TO_STRING_OPTION_LOCATION = 2;
    public static final int TO_STRING_OPTION_DISTANCE = 4;

    @Override
    public void init() {
        super.init();

        asDouble("latitude", "longitude", "distance");
        asString("comment");
    }

    public void setLocation(String location){
        setValue("location", location);
    }
    public String getLocation(){
        return getCasted("location");
    }

    public void setLatitude(double latitude){ setValue("latitude", latitude); }
    public double getLatitude(){ return getCasted("latitude"); }
    public void setLongitude(double longitude){ setValue("longitude", longitude); }
    public double getLongitude(){ return getCasted("longitude"); }

    public void setDistance(double distance){ setValue("distance", distance); }
    public double getDistance(){ return getCasted("distance"); }

    public String getTimezone(){ return getCasted("timezone"); }

    public String getDescription(){ return getCasted("description"); }

    public Integer getLastFeedRunID(){
        return getCasted("last_feed_run_id");
    }

    @Override
    public String toString(){
        return "ID: " + getID() + " " + getLocation() + " lat/lon: " + Double.toString(getLatitude()) + "," + Double.toString(getLongitude());
    }
}
