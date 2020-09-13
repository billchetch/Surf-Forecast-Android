package com.bulan_baru.surf_forecast;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bulan_baru.surf_forecast.data.Forecast;
import com.bulan_baru.surf_forecast.data.ForecastDay;
import com.bulan_baru.surf_forecast.data.ForecastHour;

import net.chetch.utilities.Utils;

import java.util.List;
import java.util.Calendar;

public class SurfConditionsFragment extends Fragment {
    private final static String LOG_TAG = "Surf Conditions Fragment";

    private Forecast forecast;
    private Calendar forecastDate;
    private List<ForecastHour> forecastHours;
    private List<ForecastDay> forecastDays;
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
        for(int i = 0; i < forecastHours.size(); i++) {
            ForecastHour fh = forecastHours.get(i);

            View sc = inflater.inflate(R.layout.surf_conditions, surfConditionsLayout, false);

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
                    iv.setRotation(swellDirection);
                    iv.setAlpha(alpha);
                    deg = (int) ((swellDirection + 180) % 360);
                    sd = Utils.convert(deg, Utils.Conversions.DEG_2_COMPASS) + " (" + deg + " deg)";
                }

                //wind direction
                float windDirection = Float.parseFloat(fh.getWindDirection());
                iv = sc.findViewById(R.id.windDirectionIcon);
                iv.setRotation(windDirection);
                iv.setAlpha(alpha);
                String ws = Utils.convert(fh.getWindSpeed(), Utils.Conversions.KPH_2_MPH, 0) + " mph";
                deg = (int) ((windDirection + 180) % 360);
                String wd = Utils.convert(deg, Utils.Conversions.DEG_2_COMPASS) + " (" + deg + " deg)";

                //tide data
                String th = Utils.convert(Double.parseDouble(fh.getTideHeight()), Utils.Conversions.METERS_2_FEET, 0) + " ft";
                String ts = fh.getTidePosition() != null ? tidePositions[fh.getTidePosition()] : "";
                iv = sc.findViewById(R.id.tideDirection);
                iv.setRotation(fh.getTidePosition() < 5 ? 0f : 180f);
                iv.setAlpha(alpha);

                //set the display
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
                Log.e(LOG_TAG, e.getMessage() == null ? "NULL" : e.getMessage());
            }
        }

        return rootView;
    }
}
