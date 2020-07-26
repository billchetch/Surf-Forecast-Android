package com.bulan_baru.surf_forecast;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.provider.Settings.SettingNotFoundException;


import com.bulan_baru.surf_forecast.models.MainViewModel;
import com.bulan_baru.surf_forecast.data.Forecast;
import com.bulan_baru.surf_forecast.data.Location;
import com.bulan_baru.surf_forecast.services.SFLocationService;

import net.chetch.appframework.ErrorDialogFragment;
import net.chetch.appframework.GenericDialogFragment;
import net.chetch.appframework.IDialogManager;
import net.chetch.utilities.Logger;
import net.chetch.utilities.Spinner2;
import net.chetch.utilities.Utils;
import net.chetch.webservices.WebserviceViewModel;
import net.chetch.webservices.exceptions.WebserviceException;
import net.chetch.webservices.gps.GPSPosition;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MainActivity extends net.chetch.appframework.GenericActivity  implements IDialogManager {
    private static final String LOG_TAG = "Main";
    private static final int REQUEST_LOCATION_PERMISSION = 1;
    private static final int REQUEST_WRITE_PERMISSION = 2;

    MainViewModel viewModel;
    Observer dataLoadProgress  = obj -> {
        WebserviceViewModel.LoadProgress progress = (WebserviceViewModel.LoadProgress)obj;
        String state = progress.startedLoading ? "Loading" : "Loaded";
        String progressInfo = state + " " + progress.info.toLowerCase();
        setProgressInfo(progressInfo);

        Log.i("Main", "load observer " + state + " " + progress.info);
    };

    private LocationDialogFragment locationDialog;
    private LocationListener locationListener;
    private android.location.Location currentDeviceLocation;
    private GPSPosition lastGPSPosition = null;
    private Calendar lastGPSPositionUpdated = null;
    private Forecast currentForecast = null;
    private SurfConditionsFragmentAdapter surfConditionsAdapter;
    private Calendar forecastLastDisplayed;
    private Calendar forecastLastRetrieved;
    private Calendar pauseLocationUpdates;
    private int currentConditionsPage = -1;

    public enum DisplayType{
        HAND_PHONE,
        TABLET
    }

    static public DisplayType DISPLAY_TYPE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //do permissions first cos these require a restart
        if(MainViewModel.USE_DEVICE_LOCATION && !permissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION_PERMISSION);
            return;
        }

        if(MainViewModel.AUTO_BRIGHTNESS){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(getApplicationContext())) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_WRITE_PERMISSION);
                    return;
                }
            } else if(!permissionGranted(Manifest.permission.WRITE_SETTINGS)) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_SETTINGS}, REQUEST_WRITE_PERMISSION);
                return;
            }
        }

        DISPLAY_TYPE =  DisplayType.valueOf(getString(R.string.display_type));

        Log.i(LOG_TAG, "Creating main activity or device " + DISPLAY_TYPE);

        //set up some generic stuff
        includeActionBar(SettingsActivity.class);

        surfConditionsAdapter = new SurfConditionsFragmentAdapter(this, getSupportFragmentManager());

        if(DISPLAY_TYPE == DisplayType.TABLET) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        hideSurfConditions();
        hideProgress();

        View locationInfoBtn = findViewById(R.id.locationInfo);
        locationInfoBtn.setOnClickListener(view -> {
                if(currentForecast == null)return;
                Location loc = viewModel.getLocation(currentForecast.getLocationID());
                if(loc != null){
                    openLocationInfo(loc);
                }
            });


        //get the model, load data and add data observers
        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);

        //observe errors
        viewModel.getError().observe(this, t ->{
            showError(t);
            Log.e("Main", "Error: " + t.getMessage());
        });


        //observe location info data updates
        viewModel.getPositionInfo().observe(this, positionInfo-> {
            if(MainViewModel.AUTO_BRIGHTNESS){
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
                    Log.e(LOG_TAG, ex.getMessage());
                } catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }
            } //end auto brigthness

            //check for a pause in requesting location list updates
            if(pauseLocationUpdates != null && Utils.dateDiff(Calendar.getInstance(), pauseLocationUpdates, TimeUnit.SECONDS) < 30){
                Log.i(LOG_TAG,"Location updates paused");
                return;
            }

            //enough time has elapsed without user interaction so we can close various things
            closeLocationInfo();
            ((Spinner2)findViewById(R.id.surfLocation)).close();
            ViewPager viewPager = findViewById(R.id.viewPager);
            viewPager.setCurrentItem(0);


            //check to see if the device has changed significanly (either in time period or in change of location in METERS)
            GPSPosition currentPos = viewModel.getGPSPosition().getValue();
            float distance = lastGPSPosition == null ? -1 : lastGPSPosition.distanceTo(currentPos);
            if(lastGPSPosition == null || distance > 500 || Utils.dateDiff(Calendar.getInstance(), lastGPSPositionUpdated, TimeUnit.SECONDS) > 5*60){
                if(lastGPSPosition != null)Log.i(LOG_TAG,"Location changed " + distance + " meters since " + Utils.dateDiff(Calendar.getInstance(), lastGPSPositionUpdated, TimeUnit.SECONDS) + " seconds ago");
                lastGPSPosition = currentPos;
                lastGPSPositionUpdated = Calendar.getInstance();
            } else if(pauseLocationUpdates == null) {
                Log.i(LOG_TAG,"Location not significantly updated ... distance traveled " + distance + " meters since " + Utils.dateDiff(Calendar.getInstance(), lastGPSPositionUpdated, TimeUnit.SECONDS) + " seconds ago");
                return;
            }

            //by here we know we should request the list of nearby locations
            viewModel.getLocationsNearby(currentPos);
        });


        //observe new list of locations
        viewModel.getLocationsNearby().observe(this, locations->{
            if(isErrorShowing()){
                dismissError();
            }

            //now fill in the locations
            Log.i(LOG_TAG,"Retrieved " + locations.size() + " locations from server");
            if(locations.size() == 0)return;

            Spinner2 locationsSpinnner = findViewById(R.id.surfLocation);
            if(locationsSpinnner.isOpen())return;

            List<String> spinnerList = new ArrayList<>();
            for(Location loc : locations){
                spinnerList.add(loc.getLocation() + " (" + Utils.convert(loc.getDistance(), Utils.Conversions.KM_2_MILES, 1) + " miles)");
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, spinnerList);
            adapter.setDropDownViewResource(R.layout.spinner_dropdown);
            locationsSpinnner.setAdapter(adapter);
            locationsSpinnner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                    if(position < locations.size()) {
                        Location selectedLocation = locations.get(position);
                        if(selectedLocation.getLastFeedRunID() == 0){
                            showError(0, "Location " + selectedLocation.getLocation() + " does not have a forecast");
                            Log.e(LOG_TAG, "Location " + selectedLocation.getLocation() + " does not have a forecast");
                            return;
                        }

                        boolean forecastHasChanged = currentForecast == null || (currentForecast.getFeedRunID() != selectedLocation.getLastFeedRunID()) || currentForecast.getLocationID() != selectedLocation.getID();
                        long secsSinceForecastRetrieved = forecastLastRetrieved == null ? -1 : Utils.dateDiff(Calendar.getInstance(), forecastLastRetrieved, TimeUnit.SECONDS);
                        if(forecastHasChanged || secsSinceForecastRetrieved > 60*60){
                            Log.i(LOG_TAG, "Retrieving forecast because " + (forecastHasChanged ? "it has changed" : "last retrieved " + secsSinceForecastRetrieved + " secs ago"));
                            setPauseLocationUpdates(true);
                            showProgress();
                            hideSurfConditions();
                            viewModel.getForecast(selectedLocation.getID());
                        } else {
                            setPauseLocationUpdates(false);
                            if(Utils.dateDiff(Calendar.getInstance(), forecastLastDisplayed, TimeUnit.SECONDS) > 10 * 60){
                                showForecast(currentForecast); //this is to allow for time updates
                                Log.i(LOG_TAG, "Re-showing forecast to allow for time changes");
                            }
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parentView) {
                    // your code here
                }
            });
        }); //end observe nearby locations data

        //observe new forecast data
        viewModel.getForecast().observe(this, forecast->{
            forecastLastRetrieved = Calendar.getInstance();
            showForecast(forecast);
        });

        Logger.info("Main activity created with: use_device_location=" + MainViewModel.USE_DEVICE_LOCATION + ", auto_brightness=" + MainViewModel.AUTO_BRIGHTNESS + " and display type=" + DISPLAY_TYPE);
        Log.i(LOG_TAG, "onCreate ended");

        //start loading in data
        showProgress();
        viewModel.loadData(dataLoadProgress).observe(services -> {
            startLocationUpdates(false);
        });


    } //end onCreate

    private void startLocationUpdates(boolean restart){
        lastGPSPosition = null;
        setPauseLocationUpdates(false);
        stopTimer();

        if(MainViewModel.USE_DEVICE_LOCATION) {
            LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(android.location.Location location) {
                    if(currentDeviceLocation == null && location != null){
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
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, locationListener);
        } else {
            Log.i(LOG_TAG, "Using server GPS");
            viewModel.getLatestGPSPosition();
        }

        startTimer(30);
    }

    @Override
    protected int onTimer(){
        if(MainViewModel.USE_DEVICE_LOCATION) {
            if(currentDeviceLocation != null) {
                viewModel.setGPSPositionFromLocation(currentDeviceLocation);
            }
        } else {
            viewModel.getLatestGPSPosition();
        }

        return super.onTimer();
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
                showProgress();
                hideSurfConditions();
                viewModel.loadData(dataLoadProgress).observe(services-> {
                    startLocationUpdates(true);
                });
            }
            Log.i(LOG_TAG, "Error dialog onDialogPositiveClick");
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
        Log.i(LOG_TAG, pause ? "Location updates paused" : "Location updates resumed");
    }


    public void openLocationInfo(Location location){
        Log.i(LOG_TAG, "Open location info");
        locationDialog = new LocationDialogFragment();
        locationDialog.location = location;
        locationDialog.show(getSupportFragmentManager(), "LocationDialog");
        setPauseLocationUpdates(true);
    }

    public void closeLocationInfo(){
        if(locationDialog != null)locationDialog.dismiss();
        locationDialog = null;
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
}
