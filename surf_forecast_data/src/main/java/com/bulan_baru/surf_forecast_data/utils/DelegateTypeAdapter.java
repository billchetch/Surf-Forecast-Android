package com.bulan_baru.surf_forecast_data.utils;

import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Type;

public class DelegateTypeAdapter<T> extends TypeAdapter<T> {
    protected TypeAdapter<T> delegate;

    static public boolean isAdapterForType(Type t){
        return false;
    }

    public void setDelegate(TypeAdapter<T> delegate){
        this.delegate = delegate;
    }

    public void write(JsonWriter out, T value) throws IOException {
        delegate.write(out, value);
    }

    public T read(JsonReader in) throws IOException {
        return delegate.read(in);
    }

}
