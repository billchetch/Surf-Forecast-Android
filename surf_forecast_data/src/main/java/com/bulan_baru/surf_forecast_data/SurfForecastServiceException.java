package com.bulan_baru.surf_forecast_data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import retrofit2.Response;

public class SurfForecastServiceException extends Exception {

    public class ErrorResponse{
        @SerializedName("http_code")
        public int httpCode;
        @SerializedName("error_code")
        public int errorCode;
        @SerializedName("message")
        public String message;
    }

    public static SurfForecastServiceException create(Response<?> response){
        SurfForecastServiceException sfex = null;
        String errorBody = null;
        try {
            errorBody = response.errorBody().string();
            Gson gson = new GsonBuilder().create();
            ErrorResponse errorResponse = gson.fromJson(errorBody, ErrorResponse.class);
            sfex = new SurfForecastServiceException(errorResponse.message, errorResponse.errorCode, response.code());
        } catch (Exception e) {
            errorBody = "No error message supplied";
            sfex = new SurfForecastServiceException(errorBody, 0, response.code());
        }
        return sfex;
    }

    private int errorCode;
    private int httpCode;

    SurfForecastServiceException(String message, int errorCode, int httpCode){
        super(message);
        this.errorCode = errorCode;
        this.httpCode = httpCode;
    }

    public void setHttpCode(int hc){ this.httpCode = hc; }
    public int getHttpCode(){ return httpCode; }
    public int getErrorCode(){ return errorCode; }
}
