package com.bulan_baru.surf_forecast.data;

import net.chetch.webservices.AboutService;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;


public interface ISurfForecastService {
    String SERVICE_NAME = "Surf Forecast";

    @GET("about")
    Call<AboutService> getAbout();

    @GET("position-info")
    Call<PositionInfo> getPositionInfo(@Query("date") String dateString, @Query("lat") double latitude, @Query("lon") double longitude);

    @GET("locations")
    Call<Locations> getLocations(@Query("lat") double latitude, @Query("lon") double longitude, @Query("distance") float maxDistance);

    @GET("sources")
    Call<Sources> getSources();

    @GET("forecast-daylight/{locationID}")
    Call<Forecast> getForecast(@Path("locationID") int locationID);

    @POST("digest")
    Call<Digest> postDigest(@Body Digest digest);
}
