package com.bulan_baru.surf_forecast_data;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.util.Log;

import com.bulan_baru.surf_forecast_data.utils.Utils;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Call;
import retrofit2.Response;

@Singleton
public class SurfForecastRepository{
    public static final int ERROR_LOCATIONS_NOT_AVAILBLE = 2;
    public static final int ERROR_FORECAST_FOR_LOCATION_NOT_AVAILABLE = 3;
    public static final int ERROR_SERVICE_UNREACHABLE = 4;

    private static final long RETRY_AFTER = (long)(Utils.MINUTE_IN_MILLIS*2);
    private static final String LOG_TAG = "SF Repo";

    private SurfForecastServiceManager serviceManager;
    private ServerStatus serverStatus;
    private ClientDevice device;
    private boolean serviceAvailable = true;
    private Calendar serviceLastAvailable = null;
    private boolean useDeviceLocation = true;
    private float maxDistance = -1;

    //so that we can observe over time
    private final MutableLiveData<Throwable> liveDataServiceError = new MutableLiveData<>();
    private final MutableLiveData<SurfForecastRepositoryException> liveDataRepositoryError = new MutableLiveData<>();
    private final MutableLiveData<ClientDevice> liveDataDevice = new MutableLiveData<>();


    @Inject
    SurfForecastRepository(ClientDevice device, SurfForecastServiceManager serviceManager){
        this.device = device;
        this.serviceManager = serviceManager;

        liveDataDevice.setValue(device);

        liveDataServiceError.observeForever(t->{
            handleServiceError(t);
        });
    }

    protected void handleServiceError(Throwable t){
        int errorCode = 0;
        String message = t.getMessage();

        if(t instanceof SocketTimeoutException || t instanceof ConnectException || t instanceof UnknownHostException){
            errorCode = ERROR_SERVICE_UNREACHABLE;
            serviceAvailable = false;

            //wait a certain time and then reset the serviceAvailable to try again
            serviceLastAvailable = Calendar.getInstance();
            message = "Service unreachable due to " + t.getClass().getName();

        }
        if(t instanceof SurfForecastServiceException){
            SurfForecastServiceException sfsx = ((SurfForecastServiceException) t);
            errorCode = sfsx.getErrorCode();
            switch(sfsx.getHttpCode()){
                case 404:
                    serviceAvailable = true; break;

                case 500:
                    serviceAvailable = true; break;

                default:
                    serviceLastAvailable = Calendar.getInstance();
                    serviceAvailable = false;
                    break;

            }
        }

        if(message == null){
            message = "No message supplied.\n";
            message += " Class: " + t.getClass().getName() + "\n";
            message += Utils.stackTrace2String(t);
        }

        //now we set the geenral repository error using service error exception data
        liveDataRepositoryError.setValue(new SurfForecastRepositoryException(message, errorCode));
    }

    protected boolean isServiceAvailable(){
        if(!serviceAvailable && serviceLastAvailable != null && Calendar.getInstance().getTimeInMillis() - serviceLastAvailable.getTimeInMillis() > RETRY_AFTER){
            serviceAvailable = true;
        }
        return serviceAvailable;
    }

    public void flush(){
        serviceManager.cancelAllCalls();
        this.device.setID(0);
    }

    public SurfForecastServiceManager getServiceManager() {
        return serviceManager;
    }

    public SurfForecastService getService(){
        return isServiceAvailable() ? getServiceManager().getService() : null;
    }

    public ClientDevice getClientDevice(){ return this.device; }
    public LiveData<ClientDevice> clientDevice(){ return this.liveDataDevice; }

    public LiveData<SurfForecastRepositoryException> repositoryError(){ return liveDataRepositoryError; }

    public void setUseDeviceLocation(boolean useDeviceLocation){ this.useDeviceLocation = useDeviceLocation; }
    public boolean isUsingDeviceLocation(){ return useDeviceLocation; }
    public void setMaxDistance(float maxDistance){ this.maxDistance = maxDistance; }


    public LiveData<Digest> postDigest(Digest d){
        final MutableLiveData<Digest> digest = new MutableLiveData<>();

        SurfForecastService service = getService();
        if(service != null) {
            service.postDigest(d).enqueue(
                    new SurfForecastServiceCallback<Digest>(liveDataServiceError) {
                        @Override
                        public void handleResponse(Call<Digest> call, Response<Digest> response) {
                            d.id = response.body().id;
                            digest.setValue(d);
                        }
                    }
            );
        }

        return digest;
    }

    public ServerStatus getLastServerStatus(){ return serverStatus; }

    public LiveData<ServerStatus> getServerStatus(){
        final MutableLiveData<ServerStatus> serverStatusLiveData = new MutableLiveData<>();

        Log.i("SDF", "Getting server status");
        SurfForecastService service = getService();
        if(service != null) {
            service.getServerStatus().enqueue(
                    new SurfForecastServiceCallback<ServerStatus>(liveDataServiceError) {
                        @Override
                        public void handleResponse(Call<ServerStatus> call, Response<ServerStatus> response) {
                            serverStatus = response.body();
                            serverStatusLiveData.setValue(serverStatus);

                            if(!isUsingDeviceLocation()){
                                updateDeviceLocation(serverStatus.getLocation());
                            }
                        }
                    }
            );
        }
        return serverStatusLiveData;
    }

    public LiveData<LocationInfo> getLocationInfo(Calendar date, android.location.Location location){
        final MutableLiveData<LocationInfo> locationInfoLiveData = new MutableLiveData<>();

        SurfForecastService service = getService();
        if(service != null) {
            String dateString = Utils.formatDate(date, SurfForecastService.DATE_FORMAT);
            service.getLocationInfo(dateString, location.getLatitude(), location.getLongitude()).enqueue(
                    new SurfForecastServiceCallback<LocationInfo>(liveDataServiceError) {
                        @Override
                        public void handleResponse(Call<LocationInfo> call, Response<LocationInfo> response) {
                            locationInfoLiveData.setValue(response.body());
                        }
                    }
            );
        }

        return locationInfoLiveData;
    }



    public LiveData<ClientDevice> updateDeviceLocation(android.location.Location location){
        if(device != null) {
            if(location != null) {
                device.setLatitude(location.getLatitude());
                device.setLongitude(location.getLongitude());
                device.setLocationAccuracy(location.getAccuracy());
                device.setLastUpdated(new Date());
            }

            //we set this directly rather than saving to server
            liveDataDevice.setValue(device);
        }
        return liveDataDevice;
    }

    public LiveData<List<Location>> getLocationsNearby(){
        final MutableLiveData<List<Location>> locations = new MutableLiveData<>();

        SurfForecastService service = getService();
        if(service != null) {
            service.getLocations(device.getLatitude(), device.getLongitude(), maxDistance).enqueue(
                    new SurfForecastServiceCallback<List<Location>>(liveDataServiceError) {
                        @Override
                        public void handleResponse(Call<List<Location>> call, Response<List<Location>> response) {
                            locations.setValue(response.body());
                        }
                    }
            );
        }
        return locations;
    }


    public LiveData<Forecast> getForecast(int locationID){
        final MutableLiveData<Forecast> forecast = new MutableLiveData<>();

        SurfForecastService service = getService();
        if(service != null) {
            service.getForecast(locationID).enqueue(
                    new SurfForecastServiceCallback<Forecast>(liveDataServiceError) {
                        @Override
                        public void handleResponse(Call<Forecast> call, Response<Forecast> response) {
                            forecast.setValue(response.body());
                        }

                        @Override
                        public void handleError(Call<Forecast> call, Response<Forecast> response) {
                            SurfForecastServiceException sfsx = SurfForecastServiceException.create(response, ERROR_FORECAST_FOR_LOCATION_NOT_AVAILABLE);
                            handleServiceError(sfsx);
                        }
                    }
            );
        }
        return forecast;
    }
}
