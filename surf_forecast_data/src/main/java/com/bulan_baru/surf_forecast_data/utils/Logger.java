package com.bulan_baru.surf_forecast_data.utils;
import android.content.Context;
import android.text.TextUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class Logger {
    private static Context ctx = null;
    private static String logFile;
    private static String lineFeed = "\n";
    private static String tabSpace = "\t";
    private static int maxLineCount = 10;
    private static String dateFormat = "yyyy-MM-dd HH:mm:ss";

    public static void init(Context context, String filename){
        ctx = context;
        logFile = filename;
    }

    public static void init(Context context, String filename, int maxLines){
        init(context, filename);
        maxLineCount = maxLines;
    }

    private static String t(int repeat){
        String repeated = tabSpace;
        for(int i = 1; i < repeat; i++){
            repeated += tabSpace;
        }
        return repeated;
    }

    private static String t(){ return t(1); }

    public static void clear(){
        Utils.writeFile(ctx, logFile, "");
    }

    public static String read(){
        return Utils.readFile(ctx, logFile);
    }

    public static boolean write(String logType, String s, boolean prepend){
        String date = Utils.formatDate(Calendar.getInstance(), dateFormat);
        String data = date + t(2) + logType + t() + s;

        if(prepend){
            String oldData = read();
            if(oldData == null){
                oldData = "";
            } else {
                if(maxLineCount > 0){
                    List<String> lines = Arrays.asList(oldData.split("\n"));
                    if(lines.size() > maxLineCount){
                        oldData = "";
                        for(int i = 0; i < maxLineCount - 1; i++){
                          oldData += (i > 0 ? lineFeed : "") + lines.get(i);
                        }
                    }
                }
                oldData = lineFeed + oldData;
            }
            data += oldData;
        }

        return Utils.writeFile(ctx, logFile, data);
    }

    public static boolean write(String logType, String s){
        return write(logType, s, true);
    }

    public static boolean info(String s){
        return write("INFO", s);
    }

    public static boolean warning(String s){
        return write("WARNING", s);
    }

    public static boolean exception(String s){
        return write("EXCEPTION", s);
    }
}
