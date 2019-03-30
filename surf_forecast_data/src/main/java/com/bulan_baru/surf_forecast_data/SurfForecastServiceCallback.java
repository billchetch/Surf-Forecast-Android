package com.bulan_baru.surf_forecast_data;

import android.arch.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SurfForecastServiceCallback<T> implements Callback<T> {
    private MutableLiveData<Throwable> liveDataError;

    SurfForecastServiceCallback(MutableLiveData<Throwable> liveDataError){
        this.liveDataError = liveDataError;
    }

    @Override
    public void onResponse(Call<T> call, Response<T> response) {
        if(response.isSuccessful()){
            if(response.body() == null){
                handleEmptyResponse(call, response);
            } else {
                handleResponse(call, response);
            }
        } else {
            handleError(call, response);
        }
    }

    @Override
    public void onFailure(Call<T> call, Throwable t) {
        if(liveDataError != null && t != null){
            liveDataError.setValue(t);
        }
    }

    public void handleResponse(Call<T> call, Response<T> response){
        //stub to override
    }

    public void handleEmptyResponse(Call<T> call, Response<T> response){
        if(liveDataError != null) {
            liveDataError.setValue(new Exception("Empty response body"));
        }

    }

    public void handleError(Call<T> call, Response<T> response){
        if(liveDataError != null) {
            SurfForecastServiceException sfex = SurfForecastServiceException.create(response);
            liveDataError.setValue(sfex);
        }
    }

}
