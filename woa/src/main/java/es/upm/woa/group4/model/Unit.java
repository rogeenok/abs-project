package es.upm.woa.group4.model;

import es.upm.woa.ontology.Cell;
import jade.core.AID;

public class Unit {

    private AID aid;
    private Cell location;

    public AID getAid() {
        return aid;
    }

    public void setAid(AID aid) {
        this.aid = aid;
    }

    public Cell getLocation() {
        return location;
    }

    public void setLocation(Cell location) {
        this.location = location;
    }
}
