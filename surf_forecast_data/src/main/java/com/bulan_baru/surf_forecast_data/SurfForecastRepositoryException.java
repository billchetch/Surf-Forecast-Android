package com.bulan_baru.surf_forecast_data;

public class SurfForecastRepositoryException extends Exception {

    private int errorCode;

    SurfForecastRepositoryException(String message, int errorCode){
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode(){ return errorCode; }
}
