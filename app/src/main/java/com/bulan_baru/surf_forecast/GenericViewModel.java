package com.bulan_baru.surf_forecast;

//import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.location.Location;

import com.bulan_baru.surf_forecast_data.ClientDevice;
import com.bulan_baru.surf_forecast_data.Digest;
import com.bulan_baru.surf_forecast_data.SurfForecastRepository;
import com.bulan_baru.surf_forecast_data.SurfForecastRepositoryException;

public class GenericViewModel extends ViewModel {
    protected SurfForecastRepository surfForecastRepository;

    GenericViewModel() {

    }

    void init(SurfForecastRepository surfForecastRepository) {
        this.surfForecastRepository = surfForecastRepository;
    }

    ClientDevice getClientDevice() {
     return surfForecastRepository.getClientDevice();
    }

    SurfForecastRepository getSurfForecastRepository(){ return surfForecastRepository; }

    LiveData<SurfForecastRepositoryException> repositoryError(){ return surfForecastRepository.repositoryError(); }

    LiveData<Digest> postDigest(Digest digest){ return surfForecastRepository.postDigest(digest); }
}
