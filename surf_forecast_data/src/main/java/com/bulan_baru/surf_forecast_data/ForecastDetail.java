package com.bulan_baru.surf_forecast_data;

import java.util.Date;
import java.util.TimeZone;
import java.util.Calendar;

public class ForecastDetail extends DataObject {
    public Calendar date;
    private TimeZone tz;

    class WeightedDetail{
        private String weighted_values;
        private String weighted_sum;
        private String weighted_average;

        public String getString(){
            return weighted_average;
        }
    }

    public class TideData{
        public Calendar time;
        public double height;
        public String position;
        public boolean interpolated = false;
    }

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
