package com.sxw.server.util;

public class FormatFileSizeUtil {

    // 格式化存储体积，便于返回上传文件体积的检查提示信息
    public static String formatSize(Long size) {

        String unit = "B";
        if (size == null) {
            return 0 + " " + unit;
        }

        double result = (double) size;

        if (size == null || size <= 0) {
            return 0 + " " + unit;
        }
        if (size >= 1024 && size < 1048576) {
            result = (double) size / 1024;
            unit = "KB";
        } else if (size >= 1048576 && size < 1073741824) {
            result = (double) size / 1048576;
            unit = "MB";
        } else if (size >= 1073741824) {
            result = (double) size / 1073741824;
            unit = "GB";
        }
        return String.format("%.1f", result) + " " + unit;
    }

}
