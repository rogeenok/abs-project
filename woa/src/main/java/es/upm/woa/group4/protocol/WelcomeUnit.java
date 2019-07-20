package es.upm.woa.group4.protocol;

import es.upm.woa.ontology.Cell;
import jade.content.AgentAction;
import jade.core.AID;

public class WelcomeUnit implements AgentAction {

    private AID tribeAID = null;

    public AID getTribeAID() {
        return tribeAID;
    }

    public void setTribeAID(AID tribeAID) {
        this.tribeAID = tribeAID;
    }

    private Cell startingPosition = new Cell();

    public Cell getStartingPosition() {
        return startingPosition;
    }

    public void setStartingPosition(Cell startingPosition) {
        this.startingPosition.setX(startingPosition.getX());
        this.startingPosition.setY(startingPosition.getY());
    }
}
