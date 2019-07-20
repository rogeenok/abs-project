package es.upm.woa.group4.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {

    private static final String DEBUG_VARIABLE = "debug";
    private static final String FORMAT_INFO = "%s [%s] %s";

    public static void debug(String tag, String format, Object... args) {
        if (Boolean.getBoolean(DEBUG_VARIABLE)) {
            String message = String.format(format, args);
            System.out.println(formatInfoMessage(tag, message));
        }
    }

    public static void debug(String tag, String message) {
        if (Boolean.getBoolean(DEBUG_VARIABLE)) {
            System.out.println(formatInfoMessage(tag, message));
        }
    }

    public static void info(String tag, String format, Object... args) {
        String message = String.format(format, args);
        System.out.println(formatInfoMessage(tag, message));
    }

    public static void info(String tag, String message) {

        System.out.println(formatInfoMessage(tag, message));
    }

    private static String formatInfoMessage(String tag, String message) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy h:mm:ss.SSS a");
        String dateString = dateFormat.format(new Date());
        return String.format(FORMAT_INFO, dateString, tag, message);
    }

}