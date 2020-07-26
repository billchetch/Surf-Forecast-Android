package com.bulan_baru.surf_forecast;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;

import com.bulan_baru.surf_forecast.data.SurfForecastRepository;
import com.bulan_baru.surf_forecast.models.MainViewModel;
import com.bulan_baru.surf_forecast.services.SFLocationService;

import net.chetch.appframework.ChetchApplication;
import net.chetch.utilities.Logger;
import net.chetch.utilities.Utils;
import net.chetch.webservices.network.NetworkRepository;

import java.util.Calendar;


public class SurfForecastApplication extends ChetchApplication {
    static private final int TIMER_DELAY_IN_MILLIS = 5* Utils.MINUTE_IN_MILLIS;


    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            onTimer();

            timerHandler.postDelayed(this, TIMER_DELAY_IN_MILLIS);
        }
    };

    private Calendar appStarted;
    private int restartAfter = 0;

    @Override
    public void onCreate() {
        uncaughtExceptionHandler = null; //use the default one
        LOG_FILE = "bbsf.log";
        super.onCreate();

        //set client device info
        //String androidID = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);

        try {
            //set network
            setDeviceNetwork();

            //set default prefs and API Base URL
            PreferenceManager.setDefaultValues(this, R.xml.preferences, true);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            String apiBaseURL = sharedPref.getString("api_base_url", null);
            NetworkRepository.getInstance().setAPIBaseURL(apiBaseURL);

            boolean useDeviceLocation = sharedPref.getBoolean("use_device_location", true);
            MainViewModel.USE_DEVICE_LOCATION = useDeviceLocation;

            boolean autoBrightness = sharedPref.getBoolean("automatic_brightness", false);
            MainViewModel.AUTO_BRIGHTNESS = autoBrightness;

            float maxDistance = sharedPref.getFloat("max_distance", -1);
            SurfForecastRepository.getInstance().setMaxDistance(maxDistance);

            restartAfter = sharedPref.getInt("restart_app_after", 12);


            //add a network change listener to handle network state changes
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    setDeviceNetwork();
                }
            }, intentFilter);


            //fire up timer
            timerHandler.postDelayed(timerRunnable, TIMER_DELAY_IN_MILLIS);
            appStarted = Calendar.getInstance();
        } catch (Exception e){
            Log.e("Application", e.getMessage());
        }
    }

    protected void onTimer(){
        //check how long we've been running for and restart if more than a given time

        if(restartAfter < 0)return;
        long h = Utils.hoursDiff(Calendar.getInstance(), appStarted);
        if(h >= restartAfter){
            Logger.info("Application has been running for " + h + " hours so restarting");
            restartApp(2);
        }

    }

    public void setRestartAfter(int restartAfter){
        this.restartAfter = restartAfter;
    }

    public void restartApp(int delayInSecs){
        Intent intent = getPackageManager().getLaunchIntentForPackage( getPackageName() );

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, intent.getFlags());

        AlarmManager mgr = (AlarmManager) getBaseContext().getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000*delayInSecs, pendingIntent);

        Logger.info("Restarting app in " + delayInSecs + " seconds");
        System.exit(0);
    }

    protected void setDeviceNetwork(){
        Context context = getApplicationContext();

        Log.i("SFA", "setDeviceNetwork");
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if (wifiInfo != null) {
            //repositoryComponent.getRepository().getClientDevice().setDeviceNetwork(wifiInfo.getSSID());
        }
    }


}
