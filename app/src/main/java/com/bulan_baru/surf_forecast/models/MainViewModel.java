package com.bulan_baru.surf_forecast.models;


import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import android.util.Log;

import com.bulan_baru.surf_forecast.data.Forecast;
import com.bulan_baru.surf_forecast.data.Location;
import com.bulan_baru.surf_forecast.data.PositionInfo;
import com.bulan_baru.surf_forecast.data.Locations;
import com.bulan_baru.surf_forecast.data.SurfForecastRepository;

import net.chetch.webservices.DataStore;
import net.chetch.webservices.WebserviceRepository;
import net.chetch.webservices.WebserviceViewModel;
import net.chetch.webservices.gps.GPSPosition;
import net.chetch.webservices.gps.GPSRepository;
import net.chetch.webservices.network.Service;

import java.util.Calendar;


public class MainViewModel extends WebserviceViewModel {
    static public boolean USE_DEVICE_LOCATION = false;
    static public boolean AUTO_BRIGHTNESS = false;
    static public boolean SUPPRESS_ERRORS = false;

    SurfForecastRepository surfForecastRepository = SurfForecastRepository.getInstance();
    GPSRepository gpsRepository = GPSRepository.getInstance();

    private android.location.Location lastDeviceLocation;
    private Calendar deviceLocationLastUpdated;

    private MutableLiveData<GPSPosition> liveDataGPSPosition = new MutableLiveData<>();

    //this is data about the current GPS location
    private MutableLiveData<PositionInfo> liveDataPositionInfo = new MutableLiveData<>();

    //this is a list of surf locations near a given position (namely the gps of the device)
    private MutableLiveData<Locations> liveDataLocationsNearby = new MutableLiveData<>();

    //ths is the latest forecast for a give location
    private MutableLiveData<Forecast> liveDataForecast = new MutableLiveData<>();


    Locations.IDMap locationIDMap;

    public MainViewModel(){
        addRepo(surfForecastRepository);
        addRepo(gpsRepository);


        permissableServerTimeDifference = 60*2;
        serverTimeDisparityOption = ServerTimeDisparityOptions.ERROR;

        liveDataGPSPosition.observeForever(pos->{
            if(pos != null && servicesConfigured) {
                surfForecastRepository.getPositionInfo(pos).add(liveDataPositionInfo);
            }
        });

        liveDataLocationsNearby.observeForever(locations->{
            locationIDMap = locations.asIDMap();
        });

    }

    @Override
    protected void configureRepoService(WebserviceRepository<?> repo, Service service) throws Exception {
        Log.i("MVM", "Set service " + service.getValue("service_name") + " to " + service.getLocalEndpoint());
        super.configureRepoService(repo, service);
    }

    @Override
    protected ServerTimeDisparityOptions getServerTimeDisparityOption(long serverTimeDifference) {
        ServerTimeDisparityOptions defaultOption = super.getServerTimeDisparityOption(serverTimeDifference);

        Log.i("MVM", "Server time difference exceeding " + permissableServerTimeDifference);
        return defaultOption;
    }

    @Override
    public DataStore loadData(Observer observer) throws Exception{
        DataStore<?> dataStore = super.loadData(observer);
        return dataStore;
    }


    public LiveData<GPSPosition> getGPSPosition(){
        return liveDataGPSPosition;
    }

    public void setGPSPositionFromLocation(android.location.Location location){
        GPSPosition pos = new GPSPosition(location);
        liveDataGPSPosition.setValue(pos);
    }

    public LiveData<GPSPosition> getLatestGPSPosition(){
        gpsRepository.getLatestPosition().add(liveDataGPSPosition);
        return liveDataGPSPosition;
    }

    public LiveData<PositionInfo> getPositionInfo(GPSPosition pos){
        if(pos != null){
            surfForecastRepository.getPositionInfo(pos);
        }
        return liveDataPositionInfo;
    }

    public LiveData<PositionInfo> getPositionInfo(){
        return getPositionInfo(null);
    }

    public Location getLocation(int id){
        return locationIDMap.containsKey(id) ? locationIDMap.get(id) : null;
    }

    public LiveData<Locations> getLocationsNearby(GPSPosition pos){
        if(pos != null){
            surfForecastRepository.getLocationsNearby(pos).add(liveDataLocationsNearby);
        }

        return liveDataLocationsNearby;
    }

    public LiveData<Locations> getLocationsNearby(){
        return getLocationsNearby(null);
    }

    public LiveData<Forecast> getForecast(Integer locationID){
        if(locationID > 0){
            surfForecastRepository.getForecast(locationID).add(liveDataForecast);
        }

        return liveDataForecast;
    }

    public LiveData<Forecast> getForecast() {
        return getForecast(0);
    }

    /*public LiveData<Forecast> getForecast(int locationID){
        return surfForecastRepository.getForecast(locationID);
    }*/



}
