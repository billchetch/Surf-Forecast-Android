package com.bulan_baru.surf_forecast;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
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
import com.bulan_baru.surf_forecast_data.utils.Utils;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;


public class SurfConditionsOverviewFragment extends Fragment implements OnClickListener{

    public static String TIME_FORMAT = "HH:mm";

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

        public GraphSegment(long x1, long x2, double y1, double y2){
            this.x1 = x1;
            this.x2 = x2;
            this.y1 = y1;
            this.y2 = y2;
            this.phaseShift = y1 > y2 ? 1 : -1;
            this.amplitude = Math.abs(y2 - y1);
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


        public GraphView(Context context, int index, int length, Forecast forecast, Calendar firstLight, Calendar lastLight) {
            super(context);

            this.forecast = forecast;
            this.index = index;
            this.length = length;
            this.firstLight = firstLight;
            this.lastLight = lastLight;
        }

        public GraphSegment addSegment(GraphSegment segment){
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
                sg.baseline = totalY / (2.0*(double)segments.size());
                sg.yWeight = sg.amplitude / maxAmplitude;
            }

            xFirst = segments.get(0).x1;
            xLast = segments.get(segments.size() - 1).x2;

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

            int defaultMargin = 16; //maybe make this settable
            int topMargin = 54;
            int height = (canvas.getHeight() - topMargin)/length;
            int width = canvas.getWidth() - 2*defaultMargin;
            Rect graphRect = new Rect();
            graphRect.left = defaultMargin;
            graphRect.right = graphRect.left + width;
            graphRect.top =  topMargin + index*height + defaultMargin;
            graphRect.bottom = graphRect.top + height - 2*defaultMargin;

            //determine 'now' and 'first light' and 'last light'
            Calendar now = forecast.now();
            long nowInMillis = now.getTimeInMillis();
            int xNow = -1;
            if(xFirst <= nowInMillis && xLast >= nowInMillis){
                xNow = graphRect.left + (int)(((float)(nowInMillis - xFirst) / (float)(xLast - xFirst))*graphRect.width());
            }
            long flInMillis = firstLight.getTimeInMillis();
            int xFirstLight = graphRect.left + (int)(((float)(flInMillis - xFirst) / (float)(xLast - xFirst))*graphRect.width());
            long llInMillis = lastLight.getTimeInMillis();
            int xLastLight = graphRect.left + (int)(((float)(llInMillis - xFirst) / (float)(xLast - xFirst))*graphRect.width());

            //TODO: if this is one of two graphs then add label for today/tomorrow

            //draw daylight rect
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.DKGRAY);
            canvas.drawRect(Math.max(xFirstLight, graphRect.left), graphRect.top, Math.min(xLastLight, graphRect.right), graphRect.bottom, paint);

            //draw graph
            double yScale = 0.0;
            int startX = graphRect.left;
            normaliseSegments();
            for(int i = 0; i < segments.size(); i++){
                GraphSegment segment = segments.get(i);
                if(yScale == 0)yScale = graphRect.height() / (segment.amplitude/segment.yWeight);
                int xMax = (int)(segment.xWeight * graphRect.width());
                double gradient = (segment.y1 < 0 ? -1 : 1) * (Math.abs(segment.y2) - Math.abs(segment.y1)) / (double)xMax;

                for(int x = 0; x < xMax; x++) {
                    //draw the graph point
                    double theta = Math.PI*((double)x/(double)xMax);
                    double y = (gradient * x + segment.y1) * Math.cos(theta);
                    float xPos = (float)(startX + x);
                    float yPos = (float)(graphRect.bottom - yScale*segment.normalisedBy - yScale*y);

                    boolean isNow = (int)xPos == xNow;
                    boolean isLight = xPos >= xFirstLight && xPos <= xLastLight;
                    paint.setColor(isLight ? Color.LTGRAY : Color.DKGRAY);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2f);
                    paint.setPathEffect(null);
                    canvas.drawPoint(xPos, yPos, paint);

                    //if first light or last light draw marker
                    if(xPos == xFirstLight || xPos == xLastLight){
                        paint.setColor(Color.GRAY);
                        paint.setStrokeWidth(2f);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setPathEffect(null);
                        Calendar date2use = xPos == xFirstLight ? firstLight : lastLight;
                        canvas.drawLine(xPos, graphRect.top, xPos, graphRect.bottom, paint);
                        String label = Utils.formatDate(date2use, TIME_FORMAT);
                        Rect textRect = new Rect();
                        paint.getTextBounds(label, 0, label.length(), textRect);
                        canvas.drawText(label, xPos - textRect.width()/2 , graphRect.top - defaultMargin , paint);
                    }

                    //draw the tide extreme position indicator
                    if(isLight && (x == 0 || (x == xMax - 1 && i == segments.size() - 1))){
                        boolean isLast = x == xMax - 1 && i == segments.size() - 1;

                        //line
                        paint.setStrokeWidth(1f);
                        paint.setColor(Color.LTGRAY);
                        paint.setPathEffect(new DashPathEffect(new float[]{4, 4}, 0));
                        float xOffset = isLast ? -80 : 80;
                        canvas.drawLine(xPos, yPos, xPos + xOffset, yPos, paint);
                        if(i != 0 || x > 0)canvas.drawLine(xPos, yPos, xPos, graphRect.bottom, paint);

                        //label
                        paint.setStrokeWidth(2f);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setPathEffect(null);
                        String label = isLast ? segment.label2 : segment.label1;
                        Rect textRect = new Rect();
                        paint.getTextBounds(label, 0, label.length(), textRect);
                        float textXOffset = isLast ? -textRect.width() : 0;
                        float textYOffset = (x == 0 ? segment.y1 : segment.y2) < 0 ? textRect.height() : 0;
                        canvas.drawText(label, xPos + xOffset + textXOffset, yPos + textYOffset, paint);
                    }

                    //if 'now' then draw marker
                    if(isNow){
                        paint.setColor(Color.LTGRAY);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setStrokeWidth(2f);
                        canvas.drawLine(xPos, yPos, xPos, graphRect.bottom, paint);

                        String label = Utils.convert(y + segment.normalisedBy, Utils.Conversions.METERS_2_FEET, 0)+ "ft @ " + Utils.formatDate(now, TIME_FORMAT);
                        Rect textRect = new Rect();
                        paint.getTextBounds(label, 0, label.length(), textRect);
                        canvas.drawText(label, xPos + -textRect.width()/2, yPos - 8, paint);
                    }
                }
                startX += xMax;
            } //end looping segments

            //x and y axis
            paint.setColor(Color.LTGRAY);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            canvas.drawLine((float)graphRect.left, (float)graphRect.bottom, (float)graphRect.right, (float)graphRect.bottom, paint);
            canvas.drawLine((float)graphRect.left, (float)graphRect.top, (float)graphRect.left, (float)graphRect.bottom, paint);

        }

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
        for(int i = 0; i < forecastDays.size(); i++){
            ForecastDay fd = forecastDays.get(i);
            firstAndLastLight += "First light @ " + Utils.formatDate(fd.getFirstLight(), TIME_FORMAT);
            firstAndLastLight += " | Last light @ " + Utils.formatDate(fd.getLastLight(), TIME_FORMAT);
            firstAndLastLight += " ";
            List<ForecastDetail.TideData> tData = fd.getTideData();

            GraphView graphView = new GraphView(getActivity(), i, forecastDays.size(), forecast, fd.getFirstLight(), fd.getLastLight());

            ViewGroup g = (ViewGroup)rootView;
            g.addView(graphView);
            expanded = false;
            graphView.setVisibility(View.GONE);
            graphViews.add(graphView);

            //build up detail for this day and add graph segments
            for(int j = 0; j < tData.size(); j++){
                ForecastDetail.TideData td = tData.get(j);
                tideData += td.position + " of " + Utils.convert(td.height, Utils.Conversions.METERS_2_FEET, 0) + "ft @ " + Utils.formatDate(td.time, TIME_FORMAT);;
                tideData += " | ";

                //Sometimes the first light is prior to the first tide position (which will therefore be on the previous day)
                if(j == 0 && td.time.getTimeInMillis() > fd.getFirstLight().getTimeInMillis()){
                    Calendar calPrev = forecast.now();
                    calPrev.setTimeInMillis(fd.date.getTimeInMillis() - 24*3600*1000);
                    ForecastDay pd = forecast.getDay(calPrev);
                    if(pd != null) {
                        List<ForecastDetail.TideData> ptd = pd.getTideData();
                        if(ptd.size() >= 1){
                            graphView.addSegment(ptd.get(ptd.size() - 1), tData.get(0));
                        }
                    }
                }

                //add a segment
                if(j > 0) {
                    graphView.addSegment(tData.get(j - 1), td);
                }
            }
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
