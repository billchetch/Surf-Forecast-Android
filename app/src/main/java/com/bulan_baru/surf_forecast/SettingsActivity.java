package com.bulan_baru.surf_forecast;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.bulan_baru.surf_forecast.data.SurfForecastRepository;
import com.bulan_baru.surf_forecast.models.MainViewModel;
import net.chetch.appframework.SettingsActivityBase;
import net.chetch.webservices.network.NetworkRepository;

public class SettingsActivity extends SettingsActivityBase implements SharedPreferences.OnSharedPreferenceChangeListener{
    private static final String LOG_TAG = "Settings";


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {


        if(key.equals("api_base_url")){
            restartMainActivityOnFinish = true;
            try{
                String apiBaseURL = sharedPreferences.getString(key, null);
                NetworkRepository.getInstance().setAPIBaseURL(apiBaseURL);
            } catch (Exception e){
                Log.e("Settings", e.getMessage());
            }
        }

        if(key.equals("use_device_location")){
            boolean useDeviceLocation = sharedPreferences.getBoolean(key, true);
            MainViewModel.USE_DEVICE_LOCATION = useDeviceLocation;
            restartMainActivityOnFinish = true;
        }

        if(key.equals("max_distance")){
            float maxDistance = sharedPreferences.getFloat(key, -1);
            SurfForecastRepository.getInstance().setMaxDistance(maxDistance);
        }

        if(key.equals("restart_after")){
            int restartAfter = Integer.parseInt(sharedPreferences.getString(key, "12"));
            ((SurfForecastApplication)getApplication()).setRestartAfter(restartAfter);
        }

        if(key.equals("automatic_brightness")){
            boolean autoBrightness = sharedPreferences.getBoolean(key, false);
            MainViewModel.AUTO_BRIGHTNESS = autoBrightness;
            restartMainActivityOnFinish = true;
        }
    }
}
