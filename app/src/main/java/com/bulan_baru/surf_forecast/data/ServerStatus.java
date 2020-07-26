package com.bulan_baru.surf_forecast.data;

import android.location.Location;

import com.google.gson.annotations.SerializedName;

import net.chetch.webservices.DataObject;

import java.util.Calendar;
import java.util.TimeZone;

public class ServerStatus extends DataObject{

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

    public Calendar getFirstLight(){ return getCasted("first_light"); }
    public Calendar getLastLight(){ return getCasted("last_light"); }
}
