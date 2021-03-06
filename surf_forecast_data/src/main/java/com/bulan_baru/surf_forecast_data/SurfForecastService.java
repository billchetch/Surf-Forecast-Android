package com.bulan_baru.surf_forecast_data;

import java.util.List;
import java.util.Calendar;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;


public interface SurfForecastService {
    String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss Z";
    String DATE_ONLY_FORMAT = "yyyy-MM-dd Z";
    String USER_AGENT = "BBSF";

    @GET("about")
    Call<ServerStatus> getServerStatus();

    @GET("location-info")
    Call<LocationInfo> getLocationInfo(@Query("date") String dateString, @Query("lat") double latitude, @Query("lon") double longitude);

    @GET("locations")
    Call<List<Location>> getLocations(@Query("lat") double latitude, @Query("lon") double longitude, @Query("distance") float maxDistance);

    //used with device ID set to 'server' to get server location
    @GET("locations-nearby/server")
    Call<List<Location>> getLocationsNearby(@Query("distance") float maxDistance);

    @GET("sources")
    Call<List<Source>> getSources();

    @GET("forecast-daylight/{locationID}")
    Call<Forecast> getForecast(@Path("locationID") int locationID);

    @PUT("device/{deviceID}")
    Call<ClientDevice> putDevice(@Body ClientDevice device, @Path("deviceID") String deviceID);

    @POST("digest")
    Call<Digest> postDigest(@Body Digest digest);
}
