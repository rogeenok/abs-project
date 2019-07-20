package es.upm.woa.group4.util;

import jade.lang.acl.ACLMessage;

public class MsgUtil {

    public static String getStringPerformative(ACLMessage message) {
        switch (message.getPerformative()) {
            case (ACLMessage.AGREE): {
                return "AGREE";
            }
            case (ACLMessage.FAILURE): {
                return "FAILURE";
            }
            case (ACLMessage.INFORM): {
                return "INFORM";
            }
            case (ACLMessage.REFUSE): {
                return "REFUSE";
            }
            case (ACLMessage.REQUEST): {
                return "REQUEST";
            }
            case (ACLMessage.NOT_UNDERSTOOD): {
                return "NOT UNDERSTOOD";
            }
        }
        return null;
    }

}
