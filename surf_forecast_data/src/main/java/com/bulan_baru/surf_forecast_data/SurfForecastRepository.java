package com.bulan_baru.surf_forecast_data;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.util.Log;

import com.bulan_baru.surf_forecast_data.utils.Utils;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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

    private static final long RETRY_AFTER = (long)(Utils.MINUTE_IN_MILLIS*0.5);
    private static final String LOG_TAG = "SF Repo";

    private SurfForecastServiceManager serviceManager;
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
        if(t instanceof SocketTimeoutException || t instanceof ConnectException){
            errorCode = ERROR_SERVICE_UNREACHABLE;
            serviceAvailable = false;

            Log.i(LOG_TAG, "connection error: " + t.getMessage());

            //wait a certain time and then reset the serviceAvailable to try again
            serviceLastAvailable = Calendar.getInstance();

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

        Log.e(LOG_TAG, t.getMessage() != null ? t.getMessage() : "no error message available");

        //now we set the geenral repository error using service error exception data
        liveDataRepositoryError.setValue(new SurfForecastRepositoryException(t.getMessage(), errorCode));
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
            if(isUsingDeviceLocation()){
                service.getLocations(device.getLatitude(), device.getLongitude(), maxDistance).enqueue(
                        new SurfForecastServiceCallback<List<Location>>(liveDataServiceError) {
                            @Override
                            public void handleResponse(Call<List<Location>> call, Response<List<Location>> response) {
                                locations.setValue(response.body());
                            }
                        }
                );
            } else {
                //since not using the location of the device, we choose to use the location provided by the server ...
                service.getLocationsNearby(maxDistance).enqueue(
                        new SurfForecastServiceCallback<List<Location>>(liveDataServiceError) {
                            @Override
                            public void handleResponse(Call<List<Location>> call, Response<List<Location>> response) {
                                locations.setValue(response.body());
                            }
                        }
                );
            }
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
                            Forecast f = (Forecast)response.body();
                            forecast.setValue(f);
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
