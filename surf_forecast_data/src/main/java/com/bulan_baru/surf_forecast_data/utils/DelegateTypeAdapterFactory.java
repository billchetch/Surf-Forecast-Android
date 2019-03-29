package com.bulan_baru.surf_forecast_data.utils;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;

public class DelegateTypeAdapterFactory implements TypeAdapterFactory {

    private ArrayList<Class> typeAdapaters = new ArrayList<>();

    public void addTypeAdapater(Class typeAdapaterClass){
        typeAdapaters.add(typeAdapaterClass);
    }


    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type){
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        DelegateTypeAdapter<T> typeAdapter = null;
        for(Class typeAdapterClass : typeAdapaters){
           try {
               Method m = typeAdapterClass.getMethod("isAdapterForType", Type.class);
               if((boolean)m.invoke(null, type.getType())){
                   typeAdapter = (DelegateTypeAdapter<T>)typeAdapterClass.newInstance();
                   typeAdapter.setDelegate(delegate);
                   return typeAdapter;
               }
           } catch (Exception e){

           }
        }

        return delegate;
    }

}
