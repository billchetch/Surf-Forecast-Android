package com.bulan_baru.surf_forecast_data;

import com.bulan_baru.surf_forecast_data.utils.Utils;
import com.google.gson.annotations.SerializedName;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

public class Forecast extends DataObject {
    @SerializedName("feed_run_id")
    private int feedRunID;

    private Date created;

    @SerializedName("location_id")
    private int locationID;

    @SerializedName("timezone")
    private String timezone;
    private TimeZone tz;

    @SerializedName("timezone_offset")
    private String timezoneOffset;

    @SerializedName("timezone_offset_secs")
    private int timezoneOffsetSecs;

    @SerializedName("forecast_from")
    private Calendar forecastFrom;

    @SerializedName("forecast_to")
    private Calendar forecastTo;

    private TreeMap<String, ForecastHour> hours;
    private TreeMap<String, ForecastDay> days;

    public TimeZone getTimeZone(){
        if(tz == null)tz = TimeZone.getTimeZone(timezone);
        return tz;
    }

    public Calendar now(){
        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(getTimeZone());
        return cal;
    }

    public void applyTimeZone(){
        TimeZone z = getTimeZone();
        forecastFrom.setTimeZone(z);
        forecastTo.setTimeZone(z);

        for(TreeMap.Entry<String,ForecastHour> entry : hours.entrySet()){
            ForecastHour fh = entry.getValue();
            try {
                if (fh.date == null) {
                    fh.date = Utils.parseDate(entry.getKey(), SurfForecastService.DATE_FORMAT);
                }
                fh.applyTimeZone(z);
            } catch (ParseException e){

            }
        }

        for(TreeMap.Entry<String,ForecastDay> entry : days.entrySet()){
            ForecastDay fd = entry.getValue();
            try {
                if (fd.date == null) {
                    fd.date = Utils.parseDate(entry.getKey(), SurfForecastService.DATE_ONLY_FORMAT);
                }
                fd.applyTimeZone(z);
            } catch (ParseException e){

            }
        }
    }

    public int getFeedRunID(){ return feedRunID; }
    public void setFeedRunID(int feedRunID){ this.feedRunID = feedRunID; }

    public int getLocationID(){ return locationID; }

    public Date getCreated(){
        return created;
    }

    public Calendar getForecastFrom() {
        return forecastFrom;
    }

    public Calendar getForecastTo() {
        return forecastTo;
    }

    public TreeMap<String, ForecastHour> getHours(){ return hours; }
    public TreeMap<String, ForecastDay> getDays(){ return days; }

    public List<Integer> getHoursSpread(Calendar from, int steps, int minInterval, int maxInterval, int addHours){
        if(minInterval >= maxInterval)return null; //TODO: throw exception

        from = Utils.calendarSetHour(from, from.get(Calendar.HOUR_OF_DAY));

        Calendar firstLight = getFirstLight(from);
        Calendar lastLight = getLastLight(from);
        firstLight = Utils.calendarSetHour(firstLight, firstLight.get(Calendar.HOUR_OF_DAY) + 1);
        lastLight = Utils.calendarSetHour(lastLight, lastLight.get(Calendar.HOUR_OF_DAY));

        if(from.compareTo(firstLight) < 0){ //before first light so we try again with from as first light
            addHours += Utils.dateDiff(firstLight, from, TimeUnit.HOURS);
            return getHoursSpread(firstLight, steps, minInterval, maxInterval, addHours);
        } else if(from.compareTo(lastLight) > 0){ //after last light so set to first light tomorrow
            Calendar newFrom = (Calendar)from.clone();
            newFrom.add(Calendar.DATE, 1);
            newFrom = getFirstLight(newFrom);
            newFrom = Utils.calendarSetHour(newFrom, newFrom.get(Calendar.HOUR_OF_DAY));
            addHours += Utils.dateDiff(newFrom, from, TimeUnit.HOURS);
            return getHoursSpread(newFrom, steps, minInterval, maxInterval, addHours);
        } else {
            //int remainingHours = lastLight.get(Calendar.HOUR_OF_DAY) - from.get(Calendar.HOUR_OF_DAY);
            int remainingHours = (int)Utils.hoursDiff(lastLight, from);
            List<Integer> intervals = new ArrayList<>();
            intervals.add(addHours);
            for(int trySteps = steps - 1; trySteps > 0; trySteps--){
                for(int i = maxInterval; i >= minInterval; i--){
                    if(trySteps*i <= remainingHours){ //we can fit all steps in the current day
                        for(int j = 1; j <= trySteps; j++){
                            intervals.add((j*i) + addHours);
                        }
                        break;
                    }
                }
                if(intervals.size() > 1)break;
            }


            if(intervals.size() == steps){
                return intervals;
            } else {
                Calendar newFrom = (Calendar)from.clone();
                newFrom.add(Calendar.DATE, 1);
                newFrom = getFirstLight(newFrom);
                newFrom = Utils.calendarSetHour(newFrom, newFrom.get(Calendar.HOUR_OF_DAY));
                addHours += Utils.dateDiff(newFrom, from, TimeUnit.HOURS);
                List<Integer> nextIntervals = getHoursSpread(newFrom, steps - intervals.size(), minInterval, maxInterval, addHours);
                for(Integer i : nextIntervals){
                    intervals.add(i);
                }
                return intervals;
            }
        }
    }

    public List<ForecastHour> getHoursSpread(Calendar from, int steps, int minInterval, int maxInterval){
        //check first that the request lies in the forecast period
        if(!Utils.dateInRange(from, getForecastFrom(), getForecastTo())) {
            return null;
        }

        //now get an hour spread
        List<Integer> spread = getHoursSpread(from, steps, minInterval, maxInterval, 0);

        //use the hours spread to build up a calendar list
        List<Calendar> calSpread = new ArrayList<>();
        for(Integer i : spread){
            Calendar cal = (Calendar)from.clone();
            cal.add(Calendar.HOUR_OF_DAY, i);
            calSpread.add(cal);
        }

        //get daylight hours that fit the calendar spread and then collect forecast hours that fit best to that spread
        List<ForecastHour> daylightHours = getDaylightHours(Utils.calendarSetHour(calSpread.get(0), 0), Utils.calendarSetHour(calSpread.get(calSpread.size() - 1), 23));
        List<ForecastHour> hoursSpread = new ArrayList<>();
        int lastIndex = 0;
        for(Calendar cal : calSpread) {
            for (int i = lastIndex; i < daylightHours.size(); i++) {
                ForecastHour fh = daylightHours.get(i);
                if(fh.date.compareTo(cal) >= 0){
                    hoursSpread.add(fh);
                    lastIndex = i;
                    break;
                }
            }
        }
        return hoursSpread;
    }

    public List<ForecastHour> getHours(Calendar from, Calendar to){
        ArrayList<ForecastHour> filteredHours = new ArrayList<>();
        if(hours == null)return filteredHours;

        for(TreeMap.Entry<String,ForecastHour> entry : hours.entrySet()){
            ForecastHour fh = entry.getValue();
            if(fh.date.compareTo(from) >= 0 && fh.date.compareTo(to) <= 0) {
                filteredHours.add(fh);
            }
        }
        return filteredHours;
    }

    public List<ForecastHour> getHours(Date from, Date to){
        return getHours(Utils.date2cal(from), Utils.date2cal(to));
    }

    public List<ForecastHour> getDaylightHours(Calendar from, Calendar to){
        List<ForecastHour> forecastHours = getHours(from, to);
        List<ForecastHour> daylightHours = new ArrayList<>();
        for(ForecastHour hour : forecastHours){
            if(isDaylight(hour.date))daylightHours.add(hour);
        }
        return daylightHours;
    }

    public boolean isDaylight(Calendar cal){
        Calendar firstLight = getFirstLight(cal);
        Calendar lastLight = getLastLight(cal);
        return cal.compareTo(firstLight) >= 0 && cal.compareTo(lastLight) <= 0;
    }

    public boolean isDaylight(Date date){
        return isDaylight(Utils.date2cal(date));
    }

    public Calendar getFirstLight(Date date){
        return getFirstLight(Utils.date2cal(date));
    }
    public Calendar getLastLight(Date date){
        return getFirstLight(Utils.date2cal(date));
    }
    public Calendar getFirstLight(Calendar cal){
        ForecastDay fd = getDay(cal);
        if(fd != null){
            return fd.getFirstLight();
        } else {
            return null;
        }
    }

    public Calendar getLastLight(Calendar cal){
        ForecastDay fd = getDay(cal);
        if(fd != null){
            return fd.getLastLight();
        } else {
            return null;
        }
    }

    public List<ForecastDay> getDays(Calendar from, Calendar to) {
        ArrayList<ForecastDay> filteredDays = new ArrayList<>();
        if(days == null)return filteredDays;

        for(TreeMap.Entry<String,ForecastDay> entry : days.entrySet()){
            ForecastDay fd = entry.getValue();
            if(Utils.dateDiff(fd.date, from) >= 0 && Utils.dateDiff(to, fd.date) >= 0){
                filteredDays.add(fd);
            }
        }
        return filteredDays;
    }

    public List<ForecastDay> getDays(Date from, Date to){
        return getDays(Utils.date2cal(from), Utils.date2cal(to));
    }

    public ForecastDay getDay(Calendar cal){
        List<ForecastDay> fds = getDays(cal, cal);
        if(fds.size() == 1){
            return fds.get(0);
        } else {
            return null;
        }
    }


}
