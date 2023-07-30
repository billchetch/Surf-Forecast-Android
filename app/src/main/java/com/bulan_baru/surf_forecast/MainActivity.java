package com.bulan_baru.surf_forecast;

import android.Manifest;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import com.bulan_baru.surf_forecast.data.Locations;
import com.google.android.material.tabs.TabLayout;

import androidx.core.app.ActivityCompat;
import androidx.viewpager.widget.ViewPager;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.provider.Settings.SettingNotFoundException;


import com.bulan_baru.surf_forecast.models.MainViewModel;
import com.bulan_baru.surf_forecast.data.Forecast;
import com.bulan_baru.surf_forecast.data.Location;

import net.chetch.appframework.ErrorDialogFragment;
import net.chetch.appframework.GenericDialogFragment;
import net.chetch.appframework.IDialogManager;
import net.chetch.utilities.Logger;
import net.chetch.utilities.SLog;
import net.chetch.utilities.Spinner2;
import net.chetch.utilities.Utils;
import net.chetch.webservices.Webservice;
import net.chetch.webservices.WebserviceViewModel;
import net.chetch.webservices.exceptions.WebserviceException;
import net.chetch.webservices.gps.GPSPosition;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MainActivity extends net.chetch.appframework.GenericActivity implements IDialogManager {
    private static final String LOG_TAG = "Main";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_WRITE_PERMISSION = 2;

    MainViewModel viewModel;
    Observer dataLoadProgress = obj -> {
        WebserviceViewModel.LoadProgress progress = (WebserviceViewModel.LoadProgress) obj;
        String state = progress.startedLoading ? "Loading" : "Loaded";
        String progressInfo = state + " " + (progress.info == null ? "" : progress.info.toLowerCase());
        setProgressInfo(progressInfo);

        if (SLog.LOG) SLog.i("Main", "load observer " + state + " " + progress.info);
    };

    private LocationsDialogFragment locationsDialog;
    private LocationListener locationListener;
    private android.location.Location currentDeviceLocation;
    private GPSPosition lastGPSPosition = null;
    private Calendar lastGPSPositionUpdated = null;
    private Location currentLocation = null;
    private Forecast currentForecast = null;
    private SurfConditionsFragmentAdapter surfConditionsAdapter;
    private Calendar forecastLastDisplayed;
    private Calendar forecastLastRetrieved;
    private Calendar pauseLocationUpdates;
    private int currentConditionsPage = -1;

    public enum DisplayType {
        HAND_PHONE,
        TABLET
    }

    static public DisplayType DISPLAY_TYPE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //do permissions first cos these require a restart
        if (MainViewModel.USE_DEVICE_LOCATION && !permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        if (MainViewModel.AUTO_BRIGHTNESS) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!Settings.System.canWrite(getApplicationContext())) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQUEST_WRITE_PERMISSION);
                        return;
                    }
                } else if (!permissionGranted(Manifest.permission.WRITE_SETTINGS)) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_SETTINGS}, REQUEST_WRITE_PERMISSION);
                    return;
                }
            } catch (Exception e) {
                showError(0, "Error setting auto brigthness: " + e.getMessage());
            }
        }

        try {
            DISPLAY_TYPE = DisplayType.valueOf(getString(R.string.display_type));

            Configuration configuration = getResources().getConfiguration();
            if (SLog.LOG)
                SLog.i(LOG_TAG, "Metrics of width, smallest width, height: " + configuration.screenWidthDp + "," + configuration.smallestScreenWidthDp + "," + configuration.screenHeightDp);
            if (SLog.LOG) SLog.i(LOG_TAG, "Creating main activity for device " + DISPLAY_TYPE);

            //set up some generic stuff
            includeActionBar(SettingsActivity.class);

            surfConditionsAdapter = new SurfConditionsFragmentAdapter(this, getSupportFragmentManager());

            if (DISPLAY_TYPE == DisplayType.TABLET) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
        } catch (Exception e) {
            showError(0, "Error setting orientation: " + e.getMessage());
        }

        hideSurfConditions();
        hideProgress();

        TextView locationTitle = findViewById(R.id.surfLocationTitle);

        locationTitle.setOnClickListener(view -> {
            if (currentForecast == null) return;

            LiveData<Locations> liveData = viewModel.getLocationsNearby();
            if(liveData.getValue() != null){
                openLocationsDialog(liveData.getValue());
            }
        });


        //get the model, load data and add data observers
        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);

        //observe errors
        viewModel.getError().observe(this, t -> {
            if (t != null) {
                showError(t);
                if (SLog.LOG) SLog.e("Main", "Error: " + t.getMessage());
            } else {
                if (SLog.LOG) SLog.e("Main", "MainActivity observe error update with null");
            }
        });


        //observe location info data updates
        viewModel.getPositionInfo().observe(this, positionInfo -> {
            try {
                if (MainViewModel.AUTO_BRIGHTNESS) {
                    //control brightness if we are user server locations
                    Calendar now = Calendar.getInstance();
                    int brightness = 0;
                    if (Utils.dateDiff(now, positionInfo.getFirstLight(), TimeUnit.HOURS) < 0 || Utils.dateDiff(now, positionInfo.getLastLight(), TimeUnit.HOURS) > 0) {
                        //dark so lower brightness
                        brightness = 16;
                    } else {
                        //light so raise brightness
                        brightness = 255;
                    }

                    try {
                        int systemBrightness = Settings.System.getInt(getApplicationContext().getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                        if (systemBrightness != brightness) {
                            Settings.System.putInt(getApplicationContext().getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, brightness);
                        }
                    } catch (SettingNotFoundException ex) {
                        //just don't set the brightness (cos not that important)
                        if (SLog.LOG) SLog.e(LOG_TAG, ex.getMessage());
                    } catch (Exception ex) {
                        if (SLog.LOG) SLog.e(LOG_TAG, ex.getMessage());
                    }
                } //end auto brigthness

                //check for a pause in requesting location list updates
                if (pauseLocationUpdates != null && Utils.dateDiff(Calendar.getInstance(), pauseLocationUpdates, TimeUnit.SECONDS) < 30) {
                    if (SLog.LOG) SLog.i(LOG_TAG, "Location updates paused");
                    return;
                }

                //enough time has elapsed without user interaction so we can close various things
                closeLocationsDialog();
                ViewPager viewPager = findViewById(R.id.viewPager);
                viewPager.setCurrentItem(0);


                //check to see if the device has changed significanly (either in time period or in change of location in METERS)
                GPSPosition currentPos = viewModel.getGPSPosition().getValue();
                float distance = lastGPSPosition == null ? -1 : lastGPSPosition.distanceTo(currentPos);
                if (lastGPSPosition == null || distance > 500 || Utils.dateDiff(Calendar.getInstance(), lastGPSPositionUpdated, TimeUnit.SECONDS) > 5 * 60) {
                    if (lastGPSPosition != null)
                        if (SLog.LOG)
                            SLog.i(LOG_TAG, "Location changed " + distance + " meters since " + Utils.dateDiff(Calendar.getInstance(), lastGPSPositionUpdated, TimeUnit.SECONDS) + " seconds ago");
                    lastGPSPosition = currentPos;
                    lastGPSPositionUpdated = Calendar.getInstance();
                } else if (pauseLocationUpdates == null) {
                    if (SLog.LOG)
                        SLog.i(LOG_TAG, "Location not significantly updated ... distance traveled " + distance + " meters since " + Utils.dateDiff(Calendar.getInstance(), lastGPSPositionUpdated, TimeUnit.SECONDS) + " seconds ago");
                    return;
                }

                //by here we know we should request the list of nearby locations
                viewModel.getLocationsNearby(currentPos);
            } catch (Exception e) {
                if (SLog.LOG) SLog.e("Main", e.getMessage());
                showError(0, e.getMessage());
            }
        });


        //observe new list of locations ... this is updated when position info changes
        viewModel.getLocationsNearby().observe(this, locations -> {
            //we have a fresh list of surf spots nearby the current location...
            if (isErrorShowing()) {
                dismissError();
            }

            //log and check for edge conditions
            if (SLog.LOG)
                SLog.i(LOG_TAG, "Retrieved " + locations.size() + " locations from server");

            if (locations.size() == 0) return;

            //here we have a definite list of surf locations ordered by closest first
            setCurrentLocation(locations.get(0));

            boolean forecastHasChanged = currentForecast == null || (currentForecast.getFeedRunID() != currentLocation.getLastFeedRunID()) || currentForecast.getLocationID() != currentLocation.getID();
            long secsSinceForecastRetrieved = forecastLastRetrieved == null ? -1 : Utils.dateDiff(Calendar.getInstance(), forecastLastRetrieved, TimeUnit.SECONDS);
            if (forecastHasChanged || secsSinceForecastRetrieved > 60 * 60) {
                if (SLog.LOG)
                    SLog.i(LOG_TAG, "Retrieving forecast because " + (forecastHasChanged ? "it has changed" : "last retrieved " + secsSinceForecastRetrieved + " secs ago"));
                setPauseLocationUpdates(true);
                showProgress();
                hideSurfConditions();
                viewModel.getForecast(currentLocation.getID());
            } else {
                setPauseLocationUpdates(false);
                if (Utils.dateDiff(Calendar.getInstance(), forecastLastDisplayed, TimeUnit.SECONDS) > 10 * 60) {
                    showForecast(currentForecast); //this is to allow for time updates
                    if (SLog.LOG)
                        SLog.i(LOG_TAG, "Re-showing forecast to allow for time changes");
                }
            }

        }); //end observe nearby locations data

        //observe new forecast data
        viewModel.getForecast().observe(this, forecast -> {
            if (SLog.LOG) SLog.i(LOG_TAG, "Forecast retrieved");
            forecastLastRetrieved = Calendar.getInstance();
            showForecast(forecast);
        });

        Logger.info("Main activity created with: use_device_location=" + MainViewModel.USE_DEVICE_LOCATION + ", auto_brightness=" + MainViewModel.AUTO_BRIGHTNESS + " and display type=" + DISPLAY_TYPE);
        if (SLog.LOG) SLog.i(LOG_TAG, "onCreate ended");

        //start loading in data
        showProgress();
        try {
            viewModel.loadData(dataLoadProgress).observe(services -> {
                startLocationUpdates(false);
            });
        } catch (Exception e) {
            showError(0, e.getMessage());
            String errMsg = e.getMessage();
            if (errMsg == null) {
                errMsg = e.getClass().toString() + " did not have an error message";
            }
            if (SLog.LOG) SLog.e("Main", errMsg);
        }


    } //end onCreate

    @Override
    protected void onPause() {
        super.onPause();
        if(SLog.LOG)SLog.i(LOG_TAG, "onPause called");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(SLog.LOG)SLog.i(LOG_TAG, "onResume called");
    }

    private void setCurrentLocation(Location location){
        TextView locationTitle = findViewById(R.id.surfLocationTitle);
        locationTitle.setText(location.getLocationAndDistance() + " ... ");
        currentLocation = location;
    }

    @Override
    public void showError(int errorCode, String errorMessage) {
        if (MainViewModel.SUPPRESS_ERRORS) {
            Logger.error(errorMessage);
        } else {
            super.showError(errorCode, errorMessage);
        }
    }

    private void startLocationUpdates(boolean restart) {
        lastGPSPosition = null;
        setPauseLocationUpdates(false);
        stopTimer();

        if (MainViewModel.USE_DEVICE_LOCATION) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    if (currentDeviceLocation == null && location != null) {
                        viewModel.setGPSPositionFromLocation(location);
                    }
                    currentDeviceLocation = location;
                }

                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {

                }

                @Override
                public void onProviderEnabled(String s) {

                }

                @Override
                public void onProviderDisabled(String s) {

                }
            };


            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                showError("Cannot use device location as permissions have not been properly set.");
                return;
            }

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, locationListener);
            if(currentDeviceLocation == null){
                currentDeviceLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if(currentDeviceLocation == null){
                    showError("Cannot obtain last known GPS position. Please ensure your device is recieving GPS signals.");
                }
            }

            if(SLog.LOG)SLog.i(LOG_TAG, "Started using device location");
            startTimer(2);
        } else {
            if(SLog.LOG)SLog.i(LOG_TAG, "Started using server GPS");
            viewModel.getLatestGPSPosition();
            startTimer(30);
        }
    }

    @Override
    protected int onTimer(){
        int nextTimer = 30;

        try {
            if (MainViewModel.USE_DEVICE_LOCATION) {
                if (currentDeviceLocation != null) {
                    viewModel.setGPSPositionFromLocation(currentDeviceLocation);
                }
            } else {
                viewModel.getLatestGPSPosition();
            }

            return nextTimer;
        } catch (Exception e){
            showError(0, "onTimer: " + e.getMessage());
            return 0;
        }
    }


    private void showForecast(Forecast forecast){
        Calendar now = Calendar.getInstance();

        String s = "Forecast updated ";
        TimeZone tz = TimeZone.getTimeZone("UTC");
        Calendar cal = Calendar.getInstance(tz);
        long hoursDiff = Utils.dateDiff(cal, Utils.date2cal(forecast.getCreated()), TimeUnit.HOURS);
        int ageOfForecast = 0; //scale of 0 to 5
        if(hoursDiff <= 0){
            s+= "just now";
        } else if (hoursDiff < 24) {
            s+= Utils.round2string(hoursDiff, 0);
            s+= hoursDiff == 1 ? " hour" : " hours";
            s+= " ago";
            ageOfForecast = hoursDiff <= 6 ? 1 : (hoursDiff < 12 ? 2 : 3);
        } else if(hoursDiff >= 24){
            double days = Math.floor(hoursDiff/24);
            ageOfForecast = Math.min((int)days + 3, 5);
            hoursDiff = hoursDiff - (long)days*24;
            s += Utils.round2string(days, 0) + (days > 1 ? " days" : " day");
            s += (hoursDiff > 0 ? " and " + hoursDiff + " hours" : "") + " ago";
        }


        boolean expired = Utils.dateDiff(cal, forecast.getForecastTo()) > 0;
        if(expired){
            s = "Forecast expired ... " + s;
        }


        if(forecast.now().getTimeZone().getRawOffset() != Calendar.getInstance().getTimeZone().getRawOffset()){
            String stz = Utils.formatDate(forecast.now(), "Z");
            s = "Forecast times @ " + stz + " GMT ........ " + s;
        }

        //set text and colour (using ageOfForecast)
        TextView forecastInfo = findViewById(R.id.forecastInfo);
        forecastInfo.setText(s);
        int colourID = getResources().getIdentifier("age" + ageOfForecast, "color", getPackageName());
        int colour = getResources().getColor(colourID);
        forecastInfo.setTextColor(colour);

        hideProgress();
        if(!expired) {
            ViewPager viewPager = findViewById(R.id.viewPager);
            currentConditionsPage = viewPager.getCurrentItem();

            surfConditionsAdapter.setForecast(forecast);
            if (currentForecast == null) { //first time this is ever called
                //get the view pager for the surf conditions
                viewPager.setAdapter(surfConditionsAdapter);
                viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                    @Override
                    public void onPageScrolled(int i, float v, int i1) {

                    }

                    @Override
                    public void onPageSelected(int i) {
                        setPauseLocationUpdates(true);
                        currentConditionsPage = i;
                    }

                    @Override
                    public void onPageScrollStateChanged(int i) {

                    }
                });

                TabLayout tabLayout = findViewById(R.id.tabLayout);
                tabLayout.setupWithViewPager(viewPager);
            } else if(pauseLocationUpdates == null) {
                viewPager.setCurrentItem(0);
            } else {
                viewPager.setCurrentItem(currentConditionsPage);
            }

            showSurfConditions();
        }

        currentForecast = forecast;
        forecastLastDisplayed = now;
        //displayedFirstHour = surfConditionsAdapter.getFirstHour();

        Logger.info("Forecast displayed for location ID " + forecast.getLocationID());
    }

    @Override
    public void onDialogPositiveClick(GenericDialogFragment dialog){
        if(dialog instanceof ErrorDialogFragment){
            Throwable t = ((ErrorDialogFragment)dialog).throwable;
            if(t instanceof WebserviceException && !((WebserviceException)t).isServiceAvailable()){
                currentForecast = null;
                showProgress();
                hideSurfConditions();
                try {
                    viewModel.loadData(dataLoadProgress).observe(services -> {
                        startLocationUpdates(true);
                    });
                } catch(Exception e){
                    showError(e);
                }
            }
            if(SLog.LOG)SLog.i(LOG_TAG, "Error dialog onDialogPositiveClick");
        } else if(dialog instanceof LocationsDialogFragment){
            setCurrentLocation(((LocationsDialogFragment)dialog).location);
            setPauseLocationUpdates(true);
            showProgress();
            hideSurfConditions();
            viewModel.getForecast(currentLocation.getID());
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        hideSurfConditions();
    }


    @Override
    public void onRestart(){
        super.onRestart();

        showSurfConditions();
    }

    private void showSurfConditions(int visibility){
        ViewPager viewPager = findViewById(R.id.viewPager);
        viewPager.setVisibility(visibility);

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.setVisibility(visibility);
    }

    private void showSurfConditions(){ showSurfConditions(View.VISIBLE); }
    private void hideSurfConditions(){ showSurfConditions(View.INVISIBLE); }

    private void setPauseLocationUpdates(boolean pause){
        pauseLocationUpdates = pause ? Calendar.getInstance() : null;
        if(SLog.LOG)SLog.i(LOG_TAG, pause ? "Location updates paused" : "Location updates resumed");
    }


    public void openLocationsDialog(Locations locations){
        if(SLog.LOG)SLog.i(LOG_TAG, "Open locations");
        locationsDialog = new LocationsDialogFragment();
        locationsDialog.locations = locations;
        locationsDialog.show(getSupportFragmentManager(), "LocationsDialog");
        setPauseLocationUpdates(true);
    }

    public void closeLocationsDialog(){
        if(locationsDialog  !=  null){
            locationsDialog.dismiss();
            locationsDialog = null;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults){
        switch(requestCode){
            case REQUEST_LOCATION_PERMISSION:
            case REQUEST_WRITE_PERMISSION:
                ((SurfForecastApplication) getApplication()).restartApp(1);
                break;

            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode){
            case REQUEST_WRITE_PERMISSION:
            case REQUEST_LOCATION_PERMISSION:
                ((SurfForecastApplication) getApplication()).restartApp(1);
                break;
        }
    }

    @Override
    public void openAbout() {
        super.openAbout();
        try {
            String lf = "\n";
            String s = "GPS position: " + lastGPSPosition.getLatitude() + "," + lastGPSPosition.getLongitude() + lf;
            s += "GPS updated on: " + Utils.formatDate(lastGPSPositionUpdated, Webservice.DEFAULT_DATE_FORMAT) + lf;
            s += "Last Feed Run ID: " + currentForecast.getFeedRunID() + lf;
            s += "Last Forecast Requested: " + Utils.formatDate(forecastLastRetrieved, Webservice.DEFAULT_DATE_FORMAT) + lf;
            aboutDialog.aboutBlurb = s;

        } catch (Exception e){

        }
    }
}
