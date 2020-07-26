package com.bulan_baru.surf_forecast.data;

import com.google.gson.stream.JsonReader;
import net.chetch.utilities.DelegateTypeAdapter;

import java.io.IOException;
import java.lang.reflect.Type;

public class ForecastTypeAdapater extends DelegateTypeAdapter<Forecast> {

    public  boolean isAdapterForType(Type t){
        return t.equals(Forecast.class);
    }

    public Forecast read(JsonReader in) throws IOException {
        Forecast f = delegate.read(in);
        f.applyTimeZone();
        return f;
    }
}
