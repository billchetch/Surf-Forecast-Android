package com.bulan_baru.surf_forecast.data;

import net.chetch.webservices.DataObject;

import java.util.Calendar;
import java.util.TimeZone;

public class ForecastDetail {

    public static TideData createTideData(TideData td){
        TideData ntd = new TideData();
        ntd.position = td.position;
        ntd.time = td.time;
        ntd.interpolated = td.interpolated;
        ntd.height = td.height;
        return ntd;
    }

    public static class TideData{
        public Calendar time;
        public double height;
        public String position;
        public boolean interpolated = false;
    }

    class WeightedDetail{
        private String weighted_values;
        private String weighted_sum;
        private String weighted_average;

        public String getString(){
            return weighted_average == null ? "0" : weighted_average;
        }
    }

    public Calendar date;
    private TimeZone tz;

    public TimeZone getTimeZone(){
        return tz;
    }

    public void applyTimeZone(TimeZone tz){
        if(this.tz == null)this.tz = tz;
        if(date != null) {
            date.setTimeZone(tz);
        }
    }
}
