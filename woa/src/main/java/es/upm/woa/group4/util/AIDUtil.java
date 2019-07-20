package es.upm.woa.group4.util;

import jade.core.AID;

public class AIDUtil {

    public static Boolean equals(AID aid1, AID aid2) {
        return aid1.getName().equals(aid2.getName());
    }

}