package com.bulan_baru.surf_forecast;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
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

import com.bulan_baru.surf_forecast_data.Forecast;
import com.bulan_baru.surf_forecast_data.ForecastDay;
import com.bulan_baru.surf_forecast_data.ForecastDetail;
import com.bulan_baru.surf_forecast_data.ForecastHour;
import com.bulan_baru.surf_forecast_data.SurfForecastService;
import com.bulan_baru.surf_forecast_data.utils.Utils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;


public class SurfConditionsOverviewFragment extends Fragment implements OnClickListener{

    public static String TIME_FORMAT = "HH:mm";
    public static String DATE_FORMAT = "EEE, d MMM";

    private View rootView;
    private boolean expanded = false;
    private Forecast forecast;
    private List<ForecastDay> forecastDays;
    private List<ForecastHour> forecastHours;
    private List<GraphView> graphViews = new ArrayList<>();

    private int timerDelay = 30;
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

        if(expanded){
            collapse();
        }

    }

    class GraphSegment{
        long x1 = 0;
        long x2 = 0;
        double y1 = 0;
        double y2 = 0;
        int phaseShift = 0;
        double xWeight = 0.0;
        double yWeight = 0.0;
        double baseline = 0.0;
        boolean normalised = false;
        double normalisedBy = 0.0;
        double amplitude = 0.0;
        String label1;
        String label2;
        int index; //position of this segment in teh list of segments that make up a graph

        public GraphSegment(long x1, long x2, double y1, double y2){
            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
            this.phaseShift = y1 > y2 ? 1 : -1;
            this.amplitude = Math.abs(y2 - y1);
        }

        public double getY(long x) throws Exception{
            if(!normalised)throw new Exception("Segment is not normalised"); //TODO: throw error
            if(x < x1 || x > x2)throw new Exception("x is out of bounds");

            long dist = x - x1;
            double gradient = (y1 < 0 ? -1 : 1) * ((double)(Math.abs(y2) - Math.abs(y1)) / (double)(x2 - x1));
            double theta = Math.PI*((double)(dist)/(double)(x2 - x1));

            double y = ((gradient * dist) + y1) * Math.cos(theta);

            return y;
        }
    }

    class GraphView extends View{

        private Paint paint = new Paint();
        private List<GraphSegment> segments = new ArrayList<>();
        private Typeface normalTypeFace = Typeface.create("Helvetica", Typeface.NORMAL);
        private Typeface boldTypeFace = Typeface.create("Helvetica", Typeface.BOLD);

        private Forecast forecast;
        private int index;
        private int length;
        private Calendar firstLight;
        private Calendar lastLight;
        private long xFirst;
        private long xLast;
        Calendar firstHour; //00:00 on the same day as first light
        Calendar lastHour; //23:59 on the same day as first light
        double maxAmplitude;

        String title;

        public GraphView(Context context, int index, int length, Forecast forecast, Calendar firstLight, Calendar lastLight) {
            super(context);

            this.forecast = forecast;
            this.index = index;
            this.length = length;
            this.firstLight = firstLight;
            this.lastLight = lastLight;

            firstHour = Utils.calendarZeroTime(firstLight);
            lastHour = (Calendar) firstHour.clone();
            lastHour.add(Calendar.DATE, 1);
            lastHour.setTimeInMillis(lastHour.getTimeInMillis() - 1);

            if (Utils.isToday(firstLight, forecast.now())) {
                title = "Today";
            } else if(Utils.isTomorrow(firstLight, forecast.now())){
                title = "Tomorrow";
            } else {
                title = Utils.formatDate(firstLight, DATE_FORMAT);
            }
        }

        public GraphSegment addSegment(GraphSegment segment){
            segment.index = segments.size();
            segments.add(segment);

            long totalXDist = 0;
            double totalY = 0;
            double maxY = 0.0;
            double minY = 0.0;
            for(GraphSegment sg : segments){
                totalXDist += sg.x2 - sg.x1;
                totalY += sg.y1 + sg.y2;
                if(Math.min(sg.y1, sg.y2) < minY)minY = Math.min(sg.y1, sg.y2);
                if(Math.max(sg.y1, sg.y2) > maxY)maxY = Math.max(sg.y1, sg.y2);
            }

            double maxAmplitude = Math.abs(maxY - minY);
            for(GraphSegment sg : segments) {
                sg.xWeight = (double) (sg.x2 - sg.x1) / (double) totalXDist;
                sg.baseline = totalY / (2.0*(double)segments.size()); //calculated average to produce mean center line
                sg.yWeight = sg.amplitude / maxAmplitude;
            }

            xFirst = segments.get(0).x1;
            xLast = segments.get(segments.size() - 1).x2;
            this.maxAmplitude = maxAmplitude;

            return segment;
        }


        public GraphSegment addSegment(long x1, long x2, double y1, double y2){
            GraphSegment segment = new GraphSegment(x1, x2, y1, y2);
            return addSegment(segment);
        }

        public GraphSegment addSegment(ForecastDetail.TideData td1, ForecastDetail.TideData td2){
            GraphSegment sg = addSegment(td1.time.getTimeInMillis(), td2.time.getTimeInMillis(), td1.height, td2.height);
            sg.label1 = Utils.convert(td1.height, Utils.Conversions.METERS_2_FEET, 0) + "ft @ " + Utils.formatDate(td1.time, TIME_FORMAT);
            sg.label2 = Utils.convert(td2.height, Utils.Conversions.METERS_2_FEET, 0) + "ft @ " + Utils.formatDate(td2.time, TIME_FORMAT);
            return sg;
        }

        public List<GraphSegment> getSegments(){
            return segments;
        }

        public GraphSegment getSegment(long x){
            for(GraphSegment sg : segments){
                if(sg.x1 <= x && sg.x2 >= x)return sg;
            }
            return null;
        }

        //moves all points to a zero line (baseLine)
        public void normaliseSegments(){
            for(GraphSegment sg : segments){
                if(sg.normalised)continue;

                sg.y1 = sg.y1 - sg.baseline;
                sg.y2 = sg.y2 - sg.baseline;
                sg.normalised = true;
                sg.normalisedBy = sg.baseline;
                sg.baseline = 0;
            }
        }



        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            //general settings
            paint.setAntiAlias(true);
            paint.setTypeface(boldTypeFace);
            paint.setTextSize(16);

            //we determine the area the graph will take up .. the graph draws from '00:00:00 to 23:59:59...'
            int defaultMargin = 16; //maybe make this settable
            int topMargin = 54;
            int height = (canvas.getHeight() - topMargin)/length;
            int width = canvas.getWidth() - 2*defaultMargin;
            Rect graphRect = new Rect();
            graphRect.left = defaultMargin;
            graphRect.right = graphRect.left + width;
            graphRect.top =  topMargin + index*height + defaultMargin;
            graphRect.bottom = graphRect.top + height - 3*defaultMargin;

            //determine 'now' and 'first light' and 'last light' positions
            Calendar now = forecast.now();
            long nowInMillis = now.getTimeInMillis();
            double xScaleMillis2Points = (double)graphRect.width() / (double)Utils.DAY_IN_MILLIS;
            double yScale2Points = graphRect.height() / maxAmplitude;
            long xFirstLight = (long)((firstLight.getTimeInMillis() - firstHour.getTimeInMillis()) * xScaleMillis2Points);
            long xLastLight = (long)((lastLight.getTimeInMillis() - firstHour.getTimeInMillis()) * xScaleMillis2Points);

            //draw daylight rect
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.DKGRAY);
            canvas.drawRect(xFirstLight + graphRect.left, graphRect.top, xLastLight + graphRect.left, graphRect.bottom, paint);


            GraphSegment segment = null;
            normaliseSegments();
            for(int i = 0; i < graphRect.width(); i++){
                long xInMillis = firstHour.getTimeInMillis() + (long)((double)i/xScaleMillis2Points);
                if(segment == null || segment.x2 < xInMillis){
                    segment = getSegment(xInMillis);
                }
                if(segment == null)continue;

                int yPos;
                try {
                    double y = segment.getY(xInMillis) + segment.normalisedBy;
                    yPos =  (int)(graphRect.bottom - (yScale2Points * y));
                } catch (Exception e){
                    Log.e("GRAPHVIEW", e.getMessage());
                    continue;
                }

                int xPos = i + graphRect.left;
                boolean isLight = i >= xFirstLight && i <= xLastLight;
                paint.setColor(isLight ? Color.LTGRAY : Color.DKGRAY);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f);
                paint.setPathEffect(null);
                canvas.drawPoint(xPos, yPos, paint);
            }

            //x and y axis
            paint.setColor(Color.LTGRAY);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            canvas.drawLine((float)graphRect.left, (float)graphRect.bottom, (float)graphRect.right, (float)graphRect.bottom, paint);
            canvas.drawLine((float)graphRect.left, (float)graphRect.top, (float)graphRect.left, (float)graphRect.bottom, paint);
            canvas.drawLine((float)graphRect.right, (float)graphRect.top, (float)graphRect.right, (float)graphRect.bottom, paint);

            //draw hour intervals
            int hourIntervals = 8;
            for(int i = 0; i <= hourIntervals; i++){
                float x = (float)graphRect.left + i*(graphRect.width()/hourIntervals);

                //tick
                paint.setColor(Color.LTGRAY);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                float y = (float)graphRect.bottom + 8;
                canvas.drawLine(x, (float)graphRect.bottom, x, y, paint);

                if(i > 0 && i < hourIntervals) {
                    //dash
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1f);
                    paint.setColor(Color.LTGRAY);
                    paint.setPathEffect(new DashPathEffect(new float[]{4, 4}, 0));
                    canvas.drawLine(x, (float)graphRect.bottom, x, (float)graphRect.top, paint);

                    //label
                    y += 12;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setStrokeWidth(1f);
                    String label = "" + i * 24 / hourIntervals;
                    canvas.drawText(label, x + 2, y, paint);
                }
            }

            //tide extremes
            for(int i = 0; i < segments.size(); i++){
                boolean isLast = i == segments.size() - 1;

                segment = segments.get(i);

                //line
                paint.setPathEffect(null);
                paint.setColor(Color.LTGRAY);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);

                float x = (float)((double)(segment.x1 - firstHour.getTimeInMillis()) * xScaleMillis2Points);
                x = x + graphRect.left;
                float y = (float)((segment.y1  + segment.normalisedBy)* yScale2Points);
                y = graphRect.bottom - y;
                float stopY = y + segment.phaseShift*defaultMargin;
                canvas.drawLine(x, y, x, stopY, paint);

                //label
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeWidth(1f);
                String label = segment.label1;
                Rect textRect = new Rect();
                paint.getTextBounds(label, 0, label.length(), textRect);
                stopY = stopY + (segment.phaseShift == 1 ? textRect.height() : 0);
                canvas.drawText(label, x - textRect.width()/2, stopY, paint);
            }

            //finally draw 'now' if relevant
            if(nowInMillis >= firstHour.getTimeInMillis() && nowInMillis < lastHour.getTimeInMillis()){
                float x = (float)((double)(nowInMillis - firstHour.getTimeInMillis()) * xScaleMillis2Points);

                //draw line
                paint.setColor(Color.LTGRAY);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f);
                paint.setPathEffect(null);
                canvas.drawLine(x, graphRect.bottom, x, graphRect.top, paint);

                //draw label
                paint.setStyle(Paint.Style.FILL);
                paint.setStrokeWidth(1f);
                String label = "Now: " + Utils.formatDate(forecast.now(),  TIME_FORMAT);

                Rect textRect = new Rect();
                paint.getTextBounds(label, 0, label.length(), textRect);
                canvas.drawText(label, x - textRect.width()/2, graphRect.top - 4, paint);
            }


            //draw title
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeWidth(1f);
            canvas.drawText(title, graphRect.left - 4, graphRect.top - 4, paint);

        }//end onDraw

    } //end GraphView

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        rootView = inflater.inflate(R.layout.fragment_surf_conditions_overview, container, false);

        rootView.setOnClickListener(this);

        return rootView;
    }



    public void setOverview(Forecast forecast, List<ForecastDay> forecastDays, List<ForecastHour> forecastHours){
        this.forecast = forecast;
        this.forecastDays = forecastDays;
        this.forecastHours = forecastHours;

        String firstAndLastLight = "";
        String tideData = "";

        graphViews.clear();
        expanded = false;

        for(int i = 0; i < forecastDays.size(); i++){
            ForecastDay fd = forecastDays.get(i);
            firstAndLastLight += "First light @ " + Utils.formatDate(fd.getFirstLight(), TIME_FORMAT);
            firstAndLastLight += " | Last light @ " + Utils.formatDate(fd.getLastLight(), TIME_FORMAT);
            firstAndLastLight += " ";

            List<ForecastDetail.TideData> tData = fd.getTideData();
            for(int j = 0; j < tData.size(); j++) {
                ForecastDetail.TideData td = tData.get(j);
                tideData += td.position + " of " + Utils.convert(td.height, Utils.Conversions.METERS_2_FEET, 0) + "ft @ " + Utils.formatDate(td.time, TIME_FORMAT);
                tideData += " | ";
            }


            Calendar cal = ((Calendar)fd.date.clone());
            cal.add(Calendar.DATE, -1);
            ForecastDay pfd = forecast.getDay(cal);
            if(pfd != null){
                tData.add(0, pfd.getTideData().get(pfd.getTideData().size() - 1));
            } else {
                //TODO:
            }

            cal.add(Calendar.DATE, 2);
            ForecastDay nfd = forecast.getDay(cal);
            if(nfd != null){
                tData.add(nfd.getTideData().get(0));
            } else {
                //TODO:
            }

            GraphView graphView = new GraphView(getActivity(), i, forecastDays.size(), forecast, fd.getFirstLight(), fd.getLastLight());
            graphView.setVisibility(View.GONE);

            //build up detail for this day and add graph segments
            for(int j = 1; j < tData.size(); j++) {
                graphView.addSegment(tData.get(j - 1), tData.get(j));
            }
            graphViews.add(graphView);
            ((ViewGroup)rootView).addView(graphView);
        }

        String overviewText = tideData + " .... " + firstAndLastLight;
        View view = getView();
        TextView otv = view.findViewById(R.id.overviewText);
        otv.setText(overviewText);

    }


    @Override
    public void onClick(View v) {
        if(expanded){
            collapse();
        } else {
            expand();
        }
    }

    public void expand(){
        expand(true);
    }

    public void expand(boolean expand){
        expanded = expand;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)rootView.getLayoutParams();

        float scaleBy = 7.0f*graphViews.size();
        lp.weight = expanded ? (1.0f/scaleBy)*lp.weight : scaleBy*lp.weight;
        rootView.setLayoutParams(lp);

        ImageView iv = rootView.findViewById(R.id.expandIcon);
        iv.setImageResource(expanded ? R.drawable.ic_round_expand_less_24px : R.drawable.ic_round_expand_more_24px);

        for(GraphView graphView : graphViews){
            graphView.setVisibility(expanded ? View.VISIBLE : View.GONE);
        }

        rootView.requestLayout();

        //set timer
        if(expanded && timerDelay > 0){
            timerHandler.postDelayed(timerRunnable, timerDelay * 1000);
        } else {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    public void collapse(){
        expand(false);
    }
}
