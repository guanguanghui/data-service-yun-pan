package com.sxw.printer;

import com.sxw.server.util.ServerTimeUtil;

public class Printer
{
    public static Printer instance;
    private static boolean isUIModel;

    public static void init(final boolean isUIModel) {
        Printer.isUIModel = isUIModel;
        Printer.instance = new Printer();
    }
    
    public void print(final String context) {
        if (Printer.instance != null) {
            System.out.println("[" + new String(ServerTimeUtil.accurateToSecond().getBytes()) + "]" + new String(context.getBytes()) + "\r\n");
        }
    }
}
