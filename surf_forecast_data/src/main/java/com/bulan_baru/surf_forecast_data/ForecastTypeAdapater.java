package com.bulan_baru.surf_forecast_data;

import com.bulan_baru.surf_forecast_data.utils.DelegateTypeAdapter;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.lang.reflect.Type;

public class ForecastTypeAdapater extends DelegateTypeAdapter<Forecast> {

    public static boolean isAdapterForType(Type t){
        return t.equals(Forecast.class);
    }

    public Forecast read(JsonReader in) throws IOException {
        Forecast f = delegate.read(in);
        f.applyTimeZone();
        return f;
    }
}
