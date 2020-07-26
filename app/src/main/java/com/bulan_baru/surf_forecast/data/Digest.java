package com.bulan_baru.surf_forecast.data;


import com.google.gson.annotations.SerializedName;

import net.chetch.webservices.DataObject;

public class Digest extends DataObject {

    public Digest(){

    }

    public Digest(String title){
        setValue("title", title);
    }

    public Digest(String title, String digest){
        this(title);
        setValue("digest", digest);
    }

    public void addDigestInfo(String area, String info){

    }

}
