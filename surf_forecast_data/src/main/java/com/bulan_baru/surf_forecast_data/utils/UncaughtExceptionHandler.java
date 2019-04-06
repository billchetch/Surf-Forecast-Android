package com.bulan_baru.surf_forecast_data.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Spinner;

import java.io.PrintWriter;
import java.io.StringWriter;

public class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

    final public static String ACTION_UNCAUGHT_EXCEPTION = "com.bulan_baru.broadcast.UNCAUGHT_EXCEPTION";
    final public static String REPORT = "uce_report";
    public static final String LINE_FEED = "\n";

    private static Context context = null;

    public UncaughtExceptionHandler(Context context) {
        this.context = context;
    }

    public void uncaughtException(Thread thread, Throwable exception) {

        String errorReport = getErrorReport(thread, exception);

        Intent intent = new Intent();
        intent.setAction(ACTION_UNCAUGHT_EXCEPTION);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(REPORT, errorReport);
        context.startActivity(intent);

        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(10);
    }

    public String getErrorReport(Thread thread, Throwable exception){

        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));

        StringBuilder errorReport = new StringBuilder();
        errorReport.append("************ CAUSE OF ERROR ************" + LINE_FEED);
        errorReport.append(stackTrace.toString());

        return errorReport.toString();

    }

}
