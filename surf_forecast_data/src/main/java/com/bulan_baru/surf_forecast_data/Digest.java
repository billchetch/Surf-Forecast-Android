package com.bulan_baru.surf_forecast_data;


import com.google.gson.annotations.SerializedName;

public class Digest extends DataObject {

    @SerializedName("digest_title")
    private String title;

    @SerializedName("digest")
    private String digest;

    public Digest(){

    }

    public Digest(String title){
        this.title = title;
    }

    public Digest(String title, String digest){
        this.title = title;
        this.digest = digest;
    }

    public void addDigestInfo(String area, String info){

    }

}
