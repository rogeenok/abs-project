package es.upm.woa.group4.util;

import java.util.Random;

public class RandomUtil {

    public static Integer betweenNumbers(Integer low, Integer high) {
        Random random = new Random();
        return random.nextInt(high - low) + low;
    }

}
