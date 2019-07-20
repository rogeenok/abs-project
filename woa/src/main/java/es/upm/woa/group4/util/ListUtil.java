package es.upm.woa.group4.util;

import jade.core.AID;
import jade.util.leap.Iterator;

import java.util.ArrayList;
import java.util.List;

public class ListUtil {

    public static List<?> castJadeListToJavaList(jade.util.leap.List list) {
        List newList = new ArrayList<>();
        Iterator iterator = list.iterator();

        while (iterator.hasNext()) {
            newList.add(iterator.next());
        }

        return newList;
    }
 }
