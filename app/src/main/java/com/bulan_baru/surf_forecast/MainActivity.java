package com.bulan_baru.surf_forecast;

import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.TabLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.provider.Settings.SettingNotFoundException;


import com.bulan_baru.surf_forecast_data.ClientDevice;
import com.bulan_baru.surf_forecast_data.Forecast;
import com.bulan_baru.surf_forecast_data.Location;
import com.bulan_baru.surf_forecast_data.LocationInfo;
import com.bulan_baru.surf_forecast_data.ServerStatus;
import com.bulan_baru.surf_forecast_data.SurfForecastRepository;
import com.bulan_baru.surf_forecast_data.SurfForecastService;
import com.bulan_baru.surf_forecast_data.utils.Logger;
import com.bulan_baru.surf_forecast_data.utils.Spinner2;
import com.bulan_baru.surf_forecast_data.utils.TypeConverter;
import com.bulan_baru.surf_forecast_data.utils.UncaughtExceptionHandler;
import com.bulan_baru.surf_forecast_data.utils.Utils;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class MainActivity extends GenericActivity{
    private static final String LOG_TAG = "Main";

    private android.location.Location lastDeviceLocation;
    private Calendar deviceLocationLastUpdated;
    private Location currentLocation;
    private Forecast currentForecast;
    private Calendar displayedFirstHour;
    private SurfConditionsFragmentAdapter surfConditionsAdapter;
    private Calendar forecastLastDisplayed;
    private int currentConditionsPage = -1;
    private Calendar pauseLocationUpdates;
    private boolean noForecastForLocationError = false;
    private int lastShownErrorCode;
    private LocationDialogFragment locationDialog;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(null);
        setContentView(R.layout.activity_main);


        Toolbar toolbar = findViewById(R.id.main_toolbar);
        setSupportActionBar(toolbar);

        surfConditionsAdapter = new SurfConditionsFragmentAdapter(this, getSupportFragmentManager());

        hideSurfConditions();
        hideProgress();

        View locationInfo = findViewById(R.id.locationInfo);
        locationInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(LOG_TAG, "Clicked info baby ... location " + currentLocation.getID());
                if(currentLocation != null)openLocationInfo(currentLocation);
            }
        });
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

    @Override
    public void showError(int errorCode, String errorMessage){
        Log.e("GAERROR", errorMessage);
        if(errorCode == SurfForecastRepository.ERROR_FORECAST_FOR_LOCATION_NOT_AVAILABLE){
            noForecastForLocationError = true;
        }


        //suppress service unreachable errors if they occur when just refreshing data and are less than an hour old
        if(errorCode == SurfForecastRepository.ERROR_SERVICE_UNREACHABLE){
            boolean isSameLocation = (currentForecast != null && currentLocation != null && currentForecast.getLocationID() == currentLocation.getID());
            if(isSameLocation && forecastLastDisplayed != null && Utils.dateDiff(Calendar.getInstance(), forecastLastDisplayed, TimeUnit.MINUTES) < 60){
                Logger.error("Suppressed error: " + errorMessage);
                return;
            } else if(isSameLocation && forecastLastDisplayed != null){
                errorMessage += "Forecast last displayed " + Utils.formatDate(forecastLastDisplayed, SurfForecastService.DATE_FORMAT);
            }
        }

        lastShownErrorCode = errorCode;
        Logger.error("Shown error: " + errorMessage);
        super.showError(errorCode, errorMessage);
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

    /*
        Assign the particular view model and call the parent
     */
    @Override
    protected void initViewModel(Bundle savedInstanceState){
        viewModel = ViewModelProviders.of(this).get(MainViewModel.class);
        super.initViewModel(savedInstanceState);
    }


    @Override
    protected void onLocationInfo(ClientDevice device, LocationInfo locationInfo){

        //TODO: make this a setting
        //control brightness if we are user server locations
        Calendar now = Calendar.getInstance();
        int brightness = 0;
        if (Utils.dateDiff(now, locationInfo.getFirstLight(), TimeUnit.HOURS) < 0 || Utils.dateDiff(now, locationInfo.getLastLight(), TimeUnit.HOURS) > 0) {
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
        } catch (SettingNotFoundException ex){
            //just don't set the brightness (cos not that important)
        }

        //check for a pause in requesting location list updates
        if(pauseLocationUpdates != null && Utils.dateDiff(Calendar.getInstance(), pauseLocationUpdates, TimeUnit.SECONDS) < 30){
            Log.i(LOG_TAG,"Location updates paused");
            return;
        }

        //enough time has elapsed without user interaction so we can close various things
        closeLocationInfo();
        ((Spinner2)findViewById(R.id.surfLocation)).close();

        //check to see if the device has changed significanly (either in time period or in change of location in METERS)
        if(lastDeviceLocation == null || lastDeviceLocation.distanceTo(device.getLocation()) > 500 || Utils.dateDiff(Calendar.getInstance(), deviceLocationLastUpdated, TimeUnit.SECONDS) > 5*60){
            if(lastDeviceLocation != null)Log.i(LOG_TAG,"Location changed " + lastDeviceLocation.distanceTo(device.getLocation()) + " meters since " + Utils.dateDiff(Calendar.getInstance(), deviceLocationLastUpdated, TimeUnit.SECONDS) + " seconds ago");
            lastDeviceLocation = device.getLocation();
            deviceLocationLastUpdated = Calendar.getInstance();
        } else if(pauseLocationUpdates == null) {
            Log.i(LOG_TAG,"Location not significantly updated ... distance traveled " + lastDeviceLocation.distanceTo(device.getLocation()) + " meters since " + Utils.dateDiff(Calendar.getInstance(), deviceLocationLastUpdated, TimeUnit.SECONDS) + " seconds ago");
            return;
        }

        Log.i(LOG_TAG,"Retrieving locations with device at lat/lon: " + device.getLatitude() + "/" + device.getLongitude());
        //here we request locations
        ((MainViewModel)viewModel).getLocationsNearby().observe(this, locations -> {
            //we have a successful return so if there was a service unreachable error earlier that is still showing
            //then we can dismiss it here
            if(isErrorShowing() && lastShownErrorCode == SurfForecastRepository.ERROR_SERVICE_UNREACHABLE){
                dismissError();
            }

            //now fill in the locations
            Log.i(LOG_TAG,"Retrieved " + locations.size() + " locations from server");
            if(locations.size() > 0) {
                TypeConverter<Location,String> tc = new TypeConverter<Location,String>(){
                    @Override
                    public String convert(Location loc){
                        String dist = Utils.convert(loc.getDistance(), Utils.Conversions.KM_2_MILES, 1);
                        return loc.getLocation() + " (" + dist + " miles)";
                    }
                };
                List<String> spinnerList = tc.convertList(locations);

                Spinner2 locationsSpinnner = findViewById(R.id.surfLocation);
                if(!locationsSpinnner.isOpen()) {

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, spinnerList);
                    adapter.setDropDownViewResource(R.layout.spinner_dropdown);
                    locationsSpinnner.setAdapter(adapter);

                    locationsSpinnner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                            if(position < locations.size()) {
                                setPauseLocationUpdates(position != 0);
                                getForecastForLocation(locations.get(position));
                            }
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parentView) {
                            // your code here
                        }
                    });
                }
            }
        }); //end listener for locations list return
    }

    protected void getForecastForLocation(Location location){

        boolean locationHasChanged = (currentForecast != null && currentForecast.getLocationID() != location.getID());

        //if the current forecast is for the same location then don't immediately go and get a new forecast
        //instead wait X minutes before doing so
        if(!locationHasChanged && currentConditionsPage == 0){
            boolean firstHourIsAhead = displayedFirstHour != null && currentForecast.now().getTimeInMillis() <  displayedFirstHour.getTimeInMillis();
            int forecastStaleAfter = firstHourIsAhead ? 20 : 5;
            if(Utils.dateDiff(Calendar.getInstance(), forecastLastDisplayed, TimeUnit.MINUTES) < forecastStaleAfter){
                return;
            }
        }

        //if there is a request to get the forecast for the same location that produced a noForecastForLocation error then return instead
        if(currentLocation != null && currentLocation.getID() == location.getID() && noForecastForLocationError){
            return;
        }

        currentLocation = location;
        noForecastForLocationError = false;
        hideSurfConditions();
        showProgress();
        ((MainViewModel)viewModel).getForecast(currentLocation.getID()).observe(this, forecast -> {

            //if we are here then either the location has changed OR a certain period has elapsed
            setCurrentForecast(forecast);

        });
    }

    protected void setCurrentForecast(Forecast forecast){
        //if here the forecast being set is different from the one that is already set ... or it's the first forecast being set
        //... or a certain time has elapsed
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
        displayedFirstHour = surfConditionsAdapter.getFirstHour();

        Logger.info("Forecast displayed for location ID " + forecast.getLocationID());
    }

    public void openLocationInfo(Location location){
        Log.i(LOG_TAG, "Open location info");
        locationDialog = new LocationDialogFragment(location);
        locationDialog.show(getSupportFragmentManager(), "LocationDialog");
        setPauseLocationUpdates(true);
    }

    public void closeLocationInfo(){
        if(locationDialog != null)locationDialog.dismiss();
        locationDialog = null;
    }
}
