package com.bulan_baru.surf_forecast;

import android.content.Context;
import android.content.Intent;

import com.bulan_baru.surf_forecast_data.ClientDevice;
import com.bulan_baru.surf_forecast_data.utils.UncaughtExceptionHandler;

public class UCEHandler extends UncaughtExceptionHandler {

    ClientDevice device;

    public UCEHandler(Context context, String logFile, ClientDevice device) {
        super(context, logFile);

        this.device = device;
    }

    @Override
    protected Intent buildIntent(){
        Intent intent = super.buildIntent();
        intent.putExtra("crashed", "yep crashed");
        return intent;
    }

    @Override
    public String getErrorReport(Thread thread, Throwable exception){
        String errorReport = super.getErrorReport(thread, exception);

        StringBuilder xtras = new StringBuilder();
        xtras.append("DEVICE ID: " + device.getDeviceID() + LINE_FEED);
        //xtras.append("LAT/LON: " + device.getLatitude() + "/" + device.getLongitude() + LINE_FEED);

        return errorReport + xtras.toString();
    }
}
