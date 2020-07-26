package com.bulan_baru.surf_forecast.data;

import net.chetch.utilities.Utils;
import net.chetch.webservices.DataCache;
import net.chetch.webservices.DataStore;
import net.chetch.webservices.Webservice;
import net.chetch.webservices.WebserviceRepository;
import net.chetch.webservices.gps.GPSPosition;

import java.util.Calendar;


public class SurfForecastRepository extends WebserviceRepository<ISurfForecastService>{
    public static final int ERROR_LOCATIONS_NOT_AVAILBLE = 2;
    public static final int ERROR_FORECAST_FOR_LOCATION_NOT_AVAILABLE = 3;
    public static final int ERROR_SERVICE_UNREACHABLE = 4;

    private static final long RETRY_AFTER = (long)(Utils.MINUTE_IN_MILLIS*2);
    private static final String LOG_TAG = "SF Repo";

    static private SurfForecastRepository instance = null;
    static public SurfForecastRepository getInstance(){
        if(instance == null)instance = new SurfForecastRepository();
        return instance;
    }

    private float maxDistance = -1;

    //so that we can observe over time


    public SurfForecastRepository(){
        this(DataCache.MEDIUM_CACHE);
    }
    public SurfForecastRepository(int defaultCacheTime){
        super(ISurfForecastService.class, defaultCacheTime);

        this.webservice.addTypeAdapter(new ForecastTypeAdapater());
    }


    public void setMaxDistance(float maxDistance){ this.maxDistance = maxDistance; }


    public DataStore<Digest> postDigest(Digest digest){
        final DataStore<Digest> ds = new DataStore<>();

        service.postDigest(digest).enqueue(createCallback(ds));

        return ds;
    }


    public DataStore<PositionInfo> getPositionInfo(Calendar date, GPSPosition pos){
        final DataStore<PositionInfo> ds = new DataStore<>();

        String dateString = Utils.formatDate(date, Webservice.DEFAULT_DATE_FORMAT);
        service.getPositionInfo(dateString, pos.getLatitude(), pos.getLongitude()).enqueue(createCallback(ds));;

        return ds;
    }

    public DataStore<PositionInfo> getPositionInfo(GPSPosition pos){
        return getPositionInfo(Calendar.getInstance(), pos);
    }


    public DataStore<Locations> getLocationsNearby(GPSPosition pos){
        DataCache.CacheEntry<Locations> entry = cache.getCacheEntry("locations-nearby", null, DataCache.SHORT_CACHE);

        service.getLocations(pos.getLatitude(), pos.getLongitude(), maxDistance).enqueue(createCallback(entry));

        return entry;
    }


    public DataStore<Forecast> getForecast(int locationID){
        DataCache.CacheEntry<Forecast> entry = cache.getCacheEntry("forecast-" + locationID);

        service.getForecast(locationID).enqueue(createCallback(entry));

        return entry;
    }
}
