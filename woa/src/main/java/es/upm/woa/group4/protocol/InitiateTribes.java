package es.upm.woa.group4.protocol;

import jade.content.AgentAction;
import jade.core.AID;
import jade.util.leap.ArrayList;
import jade.util.leap.Iterator;
import jade.util.leap.List;

public class InitiateTribes implements AgentAction {

    private List tribeList = new ArrayList();
    public void addTribeList(AID elem) {
        List oldList = this.tribeList;
        tribeList.add(elem);
    }
    public boolean removeTribeList(AID elem) {
        List oldList = this.tribeList;
        boolean result = tribeList.remove(elem);
        return result;
    }
    public void clearAllTribeList() {
        List oldList = this.tribeList;
        tribeList.clear();
    }
    public Iterator getAllTribeList() {return tribeList.iterator(); }
    public List getTribeList() {return tribeList; }
    public void setTribeList(List l) {tribeList = l; }
}
