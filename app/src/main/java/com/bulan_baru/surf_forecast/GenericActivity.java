package com.bulan_baru.surf_forecast;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.arch.lifecycle.ViewModelProviders;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import java.lang.Thread.UncaughtExceptionHandler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import com.bulan_baru.surf_forecast_data.ClientDevice;
import com.bulan_baru.surf_forecast_data.SurfForecastRepository;
import com.bulan_baru.surf_forecast_data.SurfForecastRepositoryException;
import com.bulan_baru.surf_forecast_data.utils.Utils;

public class GenericActivity extends AppCompatActivity{
    static protected final int REQUEST_ACCESS_FINE_LOACTION = 1;

    protected GenericViewModel viewModel = null;

    protected boolean includeOptionsMenu = true;
    protected boolean includeLocation = true;

    private DialogFragment errorDialog;

    BroadcastReceiver receivePermissionRequests;
    BroadcastReceiver receiveLocationUpdates;

    //time stuff
    private int timerDelay = 30;
    boolean timerStarted = false;
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            onTimer();

            timerHandler.postDelayed(this, timerDelay*1000);
        }
    };

    protected void onTimer(){
        //stub method
    }

    protected void startTimer(int timerDelay){
        if(timerStarted)return;
        this.timerDelay = timerDelay;
        timerHandler.postDelayed(timerRunnable, timerDelay*1000);
        timerStarted = true;
    }

    protected void stopTimer(){
        timerHandler.removeCallbacks(timerRunnable);
        timerStarted = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initViewModel(savedInstanceState);

        //do basic checks
        try {
            if (!Utils.isNetworkAvailable(getApplicationContext())) {
                throw new Error("Network is not available");
            }

            if (viewModel.getSurfForecastRepository().getServiceManager().getApiBaseURL() == null) {
                throw new Error("No API base URL set");
            }
        } catch (Throwable t){
            handleGeneralError(t);
            return;
        }

        if(includeLocation && viewModel.isUsingDeviceLocation()) {
            //permission requests
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(SFLocationService.ACTION_REQUEST_PERMISSION);
            receivePermissionRequests = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.i("REQUEST PERMISSION", "Requesting permission");
                    ActivityCompat.requestPermissions(GenericActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_ACCESS_FINE_LOACTION);
                }
            };
            registerReceiver(receivePermissionRequests, intentFilter);

            //location updates
            intentFilter = new IntentFilter();
            intentFilter.addAction(SFLocationService.ACTION_UPDATED_LOCATION);
            intentFilter.addAction(SFLocationService.ACTION_LOCATION_SERVICE_ERROR);
            receiveLocationUpdates = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if(intent.getAction() == SFLocationService.ACTION_UPDATED_LOCATION) {
                        ClientDevice device = viewModel.getClientDevice();
                        if (device.getDeviceID() != null && device.getDeviceID().equals(intent.getStringExtra(SFLocationService.DEVICE_ID))) {
                            device.setLongitude(intent.getDoubleExtra(SFLocationService.LONGITUDE, 0));
                            device.setLatitude(intent.getDoubleExtra(SFLocationService.LATITUDE, 0));
                            device.setLocationAccuracy(intent.getFloatExtra(SFLocationService.LOCATION_ACCURACY, 0));
                            onDeviceLocationUpdated(device);
                        }
                    }

                    if(intent.getAction() == SFLocationService.ACTION_LOCATION_SERVICE_ERROR) {
                        showError(SFLocationService.getErrorType(intent), SFLocationService.getErrorMessage(intent));
                    }
                }
            };
            registerReceiver(receiveLocationUpdates, intentFilter);

            //after registering with location updates service we fire it up
            startLocationUpdatesService();
        }
    }

    private void startLocationUpdatesService(){
        Intent intent = new Intent(this, SFLocationService.class);

        String apiBaseURL = viewModel.getSurfForecastRepository().getServiceManager().getApiBaseURL();
        intent.putExtra(SFLocationService.API_BASE_URL, apiBaseURL);
        String ssid = viewModel.getSurfForecastRepository().getClientDevice().getDeviceNetwork();
        intent.putExtra(SFLocationService.NETWORK_SSID, ssid);
        startService(intent);
    }

    private void stopLocationUpdatesService(){
        Intent intent = new Intent(this, SFLocationService.class);
        stopService(intent);
    }

    @Override
    protected void onPause(){
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (includeLocation && viewModel.isUsingDeviceLocation()) {
            stopLocationUpdatesService();

            unregisterReceiver(receivePermissionRequests);
            unregisterReceiver(receiveLocationUpdates);
        }

        if(timerStarted)stopTimer();
    }

    protected void initViewModel(Bundle savedInstanceState){
        if(viewModel == null){
            viewModel = ViewModelProviders.of(this).get(GenericViewModel.class);
        }

        viewModel.init(((SurfForecastApplication) getApplication()).repositoryComponent.getRepository());
        viewModel.repositoryError().observe(this, t->{
            handleRepositoryError(t);
        });
    }

    protected void handleRepositoryError(SurfForecastRepositoryException t){
        String message = null;
        switch(t.getErrorCode()){
            case SurfForecastRepository.ERROR_FORECAST_FOR_LOCATION_NOT_AVAILABLE:
                message = "There is currently no forecast data for this location.  Please try again later";
                break;

            default:
                message = t.getMessage();
                break;
        }

        showError(t.getErrorCode(), message == null ? "NULL" : message);
    }

    protected void handleGeneralError(Throwable t){
        String message = t.getMessage();
        showError(0, message == null ? "NULL" : message);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults){
        switch(requestCode){
            case REQUEST_ACCESS_FINE_LOACTION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdatesService();
                } else {
                    Log.e("GACR", "permission NOT granted");
                }
                break;

            default:
                break;
        }
    }


    public void onDeviceLocationUpdated(ClientDevice device){
        //stub method
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(includeOptionsMenu) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.options_menu, menu);
        }
        return true;
    }

    public void openSettings(MenuItem menuItem){
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    public void openHelp(MenuItem menuItem){
        /*Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);*/
    }

    public void openAbout(MenuItem menuItem){
        DialogFragment dialog = new AboutDialogFragment(viewModel.getSurfForecastRepository());
        dialog.show(getSupportFragmentManager(), "AboutDialog");
    }

    public void showError(int errorCode, String errorMessage){
        Log.e("GAERROR", errorMessage);
        hideProgress();
        if(errorDialog != null)errorDialog.dismiss();

        errorDialog = new ErrorDialogFragment(errorCode, errorMessage);
        errorDialog.show(getSupportFragmentManager(), "ErrorDialog");
    }

    public void showProgress(int visibility){
        ProgressBar pb = findViewById(R.id.progressBar);
        if(pb != null){
            pb.setVisibility(visibility);
        }
    }

    public void showProgress(){ showProgress(View.VISIBLE);}
    public void hideProgress(){ showProgress(View.INVISIBLE); }
}
