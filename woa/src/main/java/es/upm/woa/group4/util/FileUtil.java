package es.upm.woa.group4.util;

import java.io.InputStream;
import java.util.Scanner;

public class FileUtil {

    public static String getContentOfResource(InputStream stream) {
        Scanner scanner = new Scanner(stream, "UTF-8");
        scanner.useDelimiter("\\A");
        String content = scanner.next();
        scanner.close();
        return content;
    }

}