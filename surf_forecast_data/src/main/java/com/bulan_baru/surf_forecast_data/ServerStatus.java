package com.bulan_baru.surf_forecast_data;

import android.location.Location;

import com.google.gson.annotations.SerializedName;

import java.util.Calendar;
import java.util.TimeZone;

public class ServerStatus {

    private Calendar now;

    private String timezone;

    @SerializedName("timezone_offset")
    private String timezoneOffset;

    private double latitude;
    private double longitude;

    @SerializedName("first_light")
    private Calendar firstLight;
    @SerializedName("last_light")
    private Calendar lastLight;

    private boolean updated = false;

    public Location getLocation(){
        Location l = new Location("");

        l.setLatitude(latitude);
        l.setLongitude(longitude);

        //TODO: set accuracy

        return l;
    }

    public Calendar serverNow() {
        Calendar cal = (Calendar)now.clone();
        TimeZone tz = TimeZone.getTimeZone(timezone);
        cal.setTimeZone(tz);
        return cal;
    }

    public Calendar getFirstLight(){ return firstLight; }
    public Calendar getLastLight(){ return lastLight; }
}
