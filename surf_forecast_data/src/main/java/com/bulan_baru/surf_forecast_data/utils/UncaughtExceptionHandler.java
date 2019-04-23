package com.bulan_baru.surf_forecast_data.utils;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Spinner;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

public class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    final public static String ACTION_UNCAUGHT_EXCEPTION = "com.bulan_baru.broadcast.UNCAUGHT_EXCEPTION";
    final public static String REPORT = "uce_report";
    public static final String LINE_FEED = "\n";

    private static Context context = null;
    private static String logFile = null;

    public UncaughtExceptionHandler(Context context, String logFile) {
        this.context = context;
        this.logFile = logFile;
    }

    public UncaughtExceptionHandler(Context context) {
        this.context = context;
    }

    protected Intent buildIntent(){
        Intent intent = new Intent();
        intent.setAction(ACTION_UNCAUGHT_EXCEPTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public void uncaughtException(Thread thread, Throwable exception) {

        String errorReport = getErrorReport(thread, exception);

        if(logFile != null) {
            Utils.writeFile(context, logFile, errorReport);
        }

        Intent intent = buildIntent();
        if(intent != null) {
            intent.putExtra(REPORT, errorReport);

            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, intent.getFlags());

            AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 2000, pendingIntent);
        }
        System.exit(2);
    }

    public String getErrorReport(Thread thread, Throwable exception){
        return Utils.stackTrace2String(exception);
    }

}
