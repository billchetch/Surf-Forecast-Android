package com.bulan_baru.surf_forecast;

import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.view.View.OnClickListener;
import android.graphics.Paint;
import android.content.Context;

import com.bulan_baru.surf_forecast_data.ForecastDay;
import com.bulan_baru.surf_forecast_data.ForecastDetail;
import com.bulan_baru.surf_forecast_data.ForecastHour;
import com.bulan_baru.surf_forecast_data.utils.Utils;

import java.util.List;


public class SurfConditionsOverviewFragment extends Fragment implements OnClickListener{

    private View rootView;
    private boolean expanded = false;
    List<ForecastDay> forecastDays;
    List<ForecastHour> forecastHours;
    GraphView graphView;

    private int timerDelay = 10;
    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            onTimer();

            timerHandler.postDelayed(this, timerDelay*1000);
        }
    };

    protected void onTimer(){
        Log.i("SCOF TIMER", "timer");


    }

    class GraphView extends View{
        private Paint paint = new Paint();

        public GraphView(Context context) {
            super(context);


        }


        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            //axis
            paint.setColor(Color.LTGRAY);
            paint.setStrokeWidth(1f);

            //x-axis
            float startX = 8;
            float startY = canvas.getHeight() - 8;
            float stopX = canvas.getWidth() - 8;
            float stopY = startY;
            canvas.drawLine(startX, startY, stopX, stopY, paint);

            //y-axis
            canvas.drawLine(startX, 8, startX, startY, paint);

        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_surf_conditions_overview, container, false);

        rootView.setOnClickListener(this);

        graphView = new GraphView(getActivity());
        ViewGroup layout = rootView.findViewById(R.id.surfConditionsOverviewLayout);
        layout.addView(graphView);

        return rootView;
    }



    public void setOverview(List<ForecastDay> forecastDays, List<ForecastHour> forecastHours){
        this.forecastDays = forecastDays;
        this.forecastHours = forecastHours;

        ForecastHour firstHour = forecastHours.get(0);
        String timeFormat = "HH:mm";
        String firstAndLastLight = "";
        String tideData = "";
        for(int i = 0; i < forecastDays.size(); i++){
            ForecastDay fd = forecastDays.get(i);
            firstAndLastLight += "First light @ " + Utils.formatDate(fd.getFirstLight(), timeFormat);
            firstAndLastLight += " | Last light @ " + Utils.formatDate(fd.getLastLight(), timeFormat);
            firstAndLastLight += " ";
            List<ForecastDetail.TideData> tData = fd.getTideData();
            for(ForecastDetail.TideData td : tData){
                tideData += td.position + " of " + Utils.convert(td.height, Utils.Conversions.METERS_2_FEET, 0) + "ft @ " + Utils.formatDate(td.time, timeFormat);
                tideData += " | ";
            }
        }
        String overviewText = tideData + " .... " + firstAndLastLight;

        View view = getView();
        TextView otv = view.findViewById(R.id.overviewText);
        otv.setText(overviewText);

    }


    @Override
    public void onClick(View v) {
        expanded = !expanded;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)rootView.getLayoutParams();

        float scaleBy = 6.0f;
        lp.weight = expanded ? (1.0f/scaleBy)*lp.weight : scaleBy*lp.weight;
        rootView.setLayoutParams(lp);

        ImageView iv = v.findViewById(R.id.expandIcon);

        iv.setImageResource(expanded ? R.drawable.ic_round_expand_less_24px : R.drawable.ic_round_expand_more_24px);

        rootView.requestLayout();


        //set timer
        if(expanded && timerDelay > 0){
            timerHandler.postDelayed(timerRunnable, timerDelay * 1000);
        } else {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }


}
