package com.sxw.server.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ExpirationDateUtil {
    public static String getExpirationDate(String timestamp, String duration){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = null;
        try {
            date = new Date(Long.parseLong(timestamp));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (date == null)
            return "";
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.HOUR, Integer.parseInt(duration));// 24小时制
        date = cal.getTime();
        cal = null;
        return sdf.format(date);
    }

    public static String getExpirationDate(String timestamp, Long duration){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = null;
        try {
            date = new Date(Long.parseLong(timestamp));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (date == null)
            return "";
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.HOUR, Math.toIntExact(duration));// 24小时制
        date = cal.getTime();
        cal = null;
        return sdf.format(date);
    }

    public static boolean isExpirationDate(String timestamp, Long duration){
        return Long.parseLong(timestamp) + duration * 3600000 <= System.currentTimeMillis() ;
    }

    public static void main(String[] args) {
        System.out.println(ExpirationDateUtil.getExpirationDate("1581668752005","480"));
    }
}
