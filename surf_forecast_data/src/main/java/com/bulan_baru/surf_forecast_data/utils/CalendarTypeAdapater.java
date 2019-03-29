package com.bulan_baru.surf_forecast_data.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class CalendarTypeAdapater extends TypeAdapter<Calendar> {

    private TypeAdapter<Date> dateTypeAdapter;
    private String format;

    public CalendarTypeAdapater(String format){
        this.format = format;

        Gson gson = new GsonBuilder()
                .setDateFormat(this.format)
                .create();
        dateTypeAdapter = gson.getAdapter(Date.class);
    }

    @Override
    public void write(JsonWriter out, Calendar value)  throws IOException{
        dateTypeAdapter.write(out, value.getTime()); //TODO: check this
    }

    @Override
    public Calendar read(JsonReader in) throws IOException{
        Date read = dateTypeAdapter.read(in);
        return Utils.date2cal(read);
    }
}
