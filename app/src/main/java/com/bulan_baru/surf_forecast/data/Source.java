package com.bulan_baru.surf_forecast.data;

import net.chetch.webservices.DataObject;

public class Source extends DataObject {

    public String getSource(){
        return getCasted("source");
    }
}
