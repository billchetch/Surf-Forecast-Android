package com.bulan_baru.surf_forecast;

import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import com.bulan_baru.surf_forecast.data.Forecast;
import com.bulan_baru.surf_forecast.data.ForecastDay;
import com.bulan_baru.surf_forecast.data.ForecastDetail;
import com.bulan_baru.surf_forecast.data.ForecastHour;

import net.chetch.utilities.SLog;
import net.chetch.utilities.Utils;

import java.util.HashMap;
import java.util.List;
import java.util.Calendar;
import java.util.Map;

public class SurfConditionsFragment extends Fragment {
    private final static String LOG_TAG = "ConditionsFragment";
    private final static String DATE_KEY_FORMAT = "yyyy-MM-dd";

    private Forecast forecast;
    private Calendar forecastDate;
    private List<ForecastHour> forecastHours;
    private List<ForecastDay> forecastDays;
    private HashMap<String, ForecastDay> forecastDaysMap = new HashMap<>();
    private HashMap<ForecastHour, View> hours2surfConditionViews = new HashMap<>();
    private boolean viewUpdated = false;

    private static final String[] tidePositions = new String[]{"Low (rising)","Low to Mid","Mid (rising)","Mid to High","High (rising)","High (dropping)","High to Mid", "Mid (dropping)", "Mid to Low", "Low (dropping)"};
    private static int[] starIDs = new int[]{R.id.star1, R.id.star2, R.id.star3, R.id.star4, R.id.star5};

    public SurfConditionsFragment(){
        //empty constructor.
    }

    public void setForecast(Forecast forecast, Calendar cal, List<ForecastHour> forecastHours){
        this.forecast = forecast;
        this.forecastHours = forecastHours;
        if(this.forecastHours == null){
            throw new NullPointerException("forecastHours cannot be null");
        }
        this.forecastDays = forecast.getDays(forecastHours.get(0).date, forecastHours.get(forecastHours.size() - 1).date);
        if(this.forecastDays == null){
            throw new NullPointerException("forecastDays cannot be null");
        }
        this.forecastDate = cal;

        for(ForecastDay fd : forecastDays){
            forecastDaysMap.put(Utils.formatDate(fd.date, DATE_KEY_FORMAT), fd);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStart(){
        super.onStart();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_surf_conditions, container, false);
        boolean startsToday = Utils.isToday(forecastHours.get(0).date, forecast.now());

        //get the layout for the overview of conditions and add data
        SurfConditionsOverviewFragment scof = (SurfConditionsOverviewFragment)getChildFragmentManager().findFragmentById(R.id.surfConditionsOverviewFragment);
        scof.setOverview(forecast, forecastDays, forecastHours);

        //get the layout for the hours conditions and add fragments
        ViewGroup surfConditionsLayout = rootView.findViewById(R.id.surfConditionsLayout);
        hours2surfConditionViews.clear();
        for(int i = 0; i < forecastHours.size(); i++) {
            ForecastHour fh = forecastHours.get(i);

            View sc = inflater.inflate(R.layout.surf_conditions, surfConditionsLayout, false);
            hours2surfConditionViews.put(fh, sc);

            try {
                //title
                String title = Utils.formatDate(fh.date, "HH:mm");
                float alpha = 1.0f;
                if(startsToday && Utils.isTomorrow(fh.date, forecast.now())){
                    title = title + " (+1 day)";
                    alpha = 0.6f;
                }

                //rating
                Integer rating = 0;
                if(fh.getBBRating() != null){
                    rating = (int) Math.round(fh.getBBRating());
                } else if (fh.getRating() != null) {
                    rating = (int) Math.round(Double.parseDouble(fh.getRating()));
                }


                for (int j = 1; j <= starIDs.length; j++) {
                    ImageView iv = sc.findViewById(starIDs[j - 1]);
                    iv.setVisibility(j <= rating ? View.VISIBLE : View.INVISIBLE);
                    iv.setAlpha(alpha);
                }

                //swell height and period
                String sh = Utils.convert(fh.getSwellHeight(), Utils.Conversions.METERS_2_FEET, 0) + " ft";
                String sp = Utils.round2string(Double.parseDouble(fh.getSwellPeriod()), 0) + " secs";

                //swell direction
                String swd = fh.getSwellDirection();
                ImageView iv = sc.findViewById(R.id.swellDirectionIcon);
                String sd;
                int deg;
                if(swd == null){
                    sd = "";
                    iv.setVisibility(View.INVISIBLE);
                } else {
                    float swellDirection = Float.parseFloat(swd);
                    iv.setVisibility(View.VISIBLE);
                    iv.setRotation((swellDirection + 180) % 360);
                    iv.setAlpha(alpha);
                    deg = (int) swellDirection;
                    sd = Utils.convert(deg, Utils.Conversions.DEG_2_COMPASS) + " (" + deg + " deg)";
                }

                //wind direction
                float windDirection = Float.parseFloat(fh.getWindDirection());
                iv = sc.findViewById(R.id.windDirectionIcon);
                iv.setRotation((windDirection + 180) % 360);
                iv.setAlpha(alpha);
                String ws = Utils.convert(fh.getWindSpeed(), Utils.Conversions.KPH_2_MPH, 0) + " mph";
                deg = (int) windDirection;
                String wd = Utils.convert(deg, Utils.Conversions.DEG_2_COMPASS) + " (" + deg + " deg)";

                //tide data
                double tideHeight = Double.parseDouble(fh.getTideHeight());
                String th = Utils.convert(tideHeight, Utils.Conversions.METERS_2_FEET, 0) + " ft";
                String ts = fh.getTidePosition() != null ? tidePositions[fh.getTidePosition()] : "";
                iv = sc.findViewById(R.id.tideDirection);
                iv.setRotation(fh.getTidePosition() < 5 ? 0f : 180f);
                iv.setAlpha(alpha);

                //set the text to display
                TextView tv = sc.findViewById(R.id.conditionsTitle);
                tv.setText(title);
                tv.setAlpha(alpha);

                tv = sc.findViewById(R.id.swellHeightAndPeriod);
                tv.setAlpha(alpha);
                tv.setText(sh + " @ " + sp);
                tv.setAlpha(alpha);
                tv = sc.findViewById(R.id.swellDirection);
                tv.setText(sd);
                tv.setAlpha(alpha);

                tv = sc.findViewById(R.id.windSpeed);
                tv.setText(ws);
                tv.setAlpha(alpha);
                tv =  sc.findViewById(R.id.windDirection);
                tv.setText(wd);
                tv.setAlpha(alpha);

                tv = sc.findViewById(R.id.tideHeightAndStatus);
                tv.setText(th + ", " + ts);
                tv.setAlpha(alpha);

                surfConditionsLayout.addView(sc);
            } catch (Exception e){ //currently just don't add the information if there is an exception (normally NULL pointer exception)
                if(SLog.LOG) SLog.e(LOG_TAG, e.getMessage() == null ? "NULL" : e.getMessage());
            }
        }

        viewUpdated = false;

        ViewTreeObserver vto = rootView.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                //vto.removeGlobalOnLayoutListener(this);
                updateView();

            }
        });

        return rootView;
    }


    private void updateView(){
        if(viewUpdated)return;
        viewUpdated = true;

        for(Map.Entry<ForecastHour, View> entry :  hours2surfConditionViews.entrySet()){
            try {
                ForecastHour fh = entry.getKey();
                View sc = entry.getValue();
                ForecastDay fd = forecastDaysMap.get(Utils.formatDate(fh.date, DATE_KEY_FORMAT));
                double tideHeight = Double.parseDouble(fh.getTideHeight());

                ImageView tideSnapshotBorder = sc.findViewById(R.id.currentTideBorder);
                int borderHeight = tideSnapshotBorder.getHeight();
                ImageView tideSnapshotBar = sc.findViewById(R.id.currentTidePosition);

                double maxTideHeight = fd.getMaxTideHeight();
                int barHeight = (int) (borderHeight * Math.min(1.0f, tideHeight / maxTideHeight));
                if (SLog.LOG) SLog.i(LOG_TAG, "Setting tide bar height to: " + barHeight);
                tideSnapshotBar.getLayoutParams().height = barHeight;
                tideSnapshotBar.setVisibility(View.VISIBLE);
                tideSnapshotBar.requestLayout();
            } catch(Exception e){
                if(SLog.LOG)Log.e(LOG_TAG, e.getMessage());
            }
        }
    }
}
