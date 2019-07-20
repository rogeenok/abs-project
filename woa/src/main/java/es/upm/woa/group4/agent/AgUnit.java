package es.upm.woa.group4.agent;

import es.upm.woa.group4.util.AIDUtil;
import es.upm.woa.group4.util.CellUtil;
import es.upm.woa.group4.util.ListUtil;
import es.upm.woa.group4.util.MsgUtil;
import es.upm.woa.ontology.*;
import es.upm.woa.group4.protocol.WelcomeUnit;
import jade.content.AgentAction;
import jade.content.Concept;
import jade.content.lang.Codec.CodecException;
import jade.content.onto.OntologyException;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.List;

import static es.upm.woa.group4.common.Constants.*;

public class AgUnit extends AgBase {

    private static final String TAG = "Unit-Tribe4";
    private AID worldAID = null;
    private AID tribeAID = null;
    private Cell position = null;
    private boolean isMovedToCell = false;
    private boolean isGameNow = false;

    @Override
    protected String getServiceType() {
        return SERVICE_TYPE_UNIT;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void setup() {
        super.setup();
        addBehaviour(new FindWorldBehaviour());
        addBehaviour(new ReceiveMessageBehaviour());
    }

    private class ReceiveMessageBehaviour extends CyclicBehaviour {

        @Override
        public void action() {
            ACLMessage message = receive();
            if (message == null) {
                block();
                return;
            }
            try {
                switch (message.getPerformative()) {
                    case (ACLMessage.REQUEST): {
                        Concept concept = extractConceptFromMessage(message);
                        if (concept instanceof CreateUnit) {
                            addBehaviour(new TriggerCreateUnitBehaviour());
                        } else if (concept instanceof MoveToCell) {
                            addBehaviour(new TriggerMoveToCellBehaviour(((MoveToCell) concept).getTargetDirection()));
                        } else if (concept instanceof CreateBuilding) {
                            addBehaviour(new TriggerCreateBuildingBehavior(((CreateBuilding) concept).getBuildingType()));
                        } else if (concept instanceof WelcomeUnit) {
                            WelcomeUnit welcomeUnit = (WelcomeUnit) concept;
                            tribeAID = welcomeUnit.getTribeAID();
                            position = welcomeUnit.getStartingPosition();
                            log("My %s says me hello. My starting position is [%d,%d]", tribeAID.getLocalName(),
                                    position.getX(), position.getY());
                            sendAgreeReply(message,welcomeUnit);
                            isGameNow = true;
                        } else if (concept instanceof ExploitResource) {
                            addBehaviour(new TriggerExploitResourceBehavior());
                        }
                        break;
                    }
                    case (ACLMessage.INFORM): {
                        Concept concept = extractConceptFromMessage(message);
                        if (concept instanceof NotifyCellDetail) {
                            NotifyCellDetail notifyCellDetail = (NotifyCellDetail) concept;
                            Cell newCell = notifyCellDetail.getNewCell();

                            // 1. unit has explored new cell
                            // 2. unit has created a building
                            // 3. another unit has explored new cell

                            if (CellUtil.hasSameCoordinates(position, newCell)) {
                                // 1. position has been changed after MoveToCell inform came before new cell explored
                                if (isMovedToCell) {
                                    // adding fix that if unit moved first to known territory and then created a building
                                    if (position.getContent().getClass().equals(newCell.getContent().getClass())) {
                                        log("I explored new %s cell [%d.%d] for my tribe", newCell.getContent().getClass().getSimpleName(),
                                                newCell.getX(), newCell.getY());
                                    } else {
                                        position.setContent(newCell.getContent());
                                        log("I have created building %s in cell [%d,%d]", ((Building) newCell.getContent()).getType(),
                                                newCell.getX(), newCell.getY());
                                    }
                                    isMovedToCell = false;
                                }
                                // 2. position is the same because unit has created some building in that cell after exploring new cell
                                else {
                                    if (newCell.getContent() instanceof Building) {
                                        log("I have created building %s in cell [%d,%d]", ((Building) newCell.getContent()).getType(),
                                                newCell.getX(), newCell.getY());
                                    } else {
                                        log("Whoops. Something wrong with %s processing. Content is %s and I moved to cell - %s",
                                                concept.getClass().getSimpleName(),
                                                newCell.getContent().getClass().getSimpleName(), isMovedToCell);
                                    }
                                }
                            } else {
                                // it's complicated to define whether cell was explored by this unit or any other unit
                                // of tribe if we don't stand in newCell
                                // 1. maybe another unit or 2. NotifyCellDetail may come before MoveToCell
                                log("A %s cell [%d,%d] was explored by me or by another unit of my tribe",
                                        newCell.getContent().getClass().getSimpleName(),
                                        newCell.getX(), newCell.getY());
                            }
                        } else if (concept instanceof NotifyNewUnit) {
                            NotifyNewUnit notifyNewUnit = (NotifyNewUnit) concept;
                            String unitName = notifyNewUnit.getNewUnit().getName();
                            Cell unitCell = notifyNewUnit.getLocation();
                            log("New unit '%s' is created at [%d,%d] by me", unitName, unitCell.getX(), unitCell.getY());
                        } else if (concept instanceof MoveToCell) {
                            MoveToCell moveToCell = (MoveToCell) concept;
                            Cell targetCell = moveToCell.getNewlyArrivedCell();
                            position = targetCell;

                            isMovedToCell = true;

                            // send the inform to my tribe
                            ACLMessage notifyMoveToCellMessage = createMessage(ACLMessage.INFORM, tribeAID);
                            notifyMoveToCellMessage.setProtocol(GameOntology.MOVETOCELL);
                            sendActionTo(tribeAID, notifyMoveToCellMessage, moveToCell);

                            // print log
                            log("I moved to cell [%d,%d] with content %s", targetCell.getX(), targetCell.getY(),
                                    targetCell.getContent().getClass().getSimpleName());
                        } else if (concept instanceof ExploitResource) {
                            ExploitResource exploitResource = (ExploitResource) concept;

                            ACLMessage exploitMessage = createMessage(ACLMessage.INFORM, tribeAID);
                            sendActionTo(tribeAID,exploitMessage,exploitResource);

                            List<GainedResource> gainedResources = (List<GainedResource>) ListUtil.castJadeListToJavaList(exploitResource.getResourceList());
                            if (gainedResources.size() == 0) {
                                log("I exploited some resources but my tribe's capacity is over :(");
                            } else {

                            String msgFormat = "I exploited some resources: ";
                            msgFormat += ((gainedResources.size() == 1) ? " %d %s" : " %d %s & %d %s");
                            String msg;
                            if (gainedResources.size() == 1) {
                                msg = String.format(msgFormat,gainedResources.get(0).getAmount(), gainedResources.get(0).getResourceName());
                            } else {
                                msg = String.format(msgFormat,gainedResources.get(0).getAmount(), gainedResources.get(0).getResourceName(),
                                        gainedResources.get(1).getAmount(), gainedResources.get(1).getResourceName());
                            }
                                log(msg);
                            }
                        } else if (concept instanceof EndOfGame) {
                            log("Uff, it's the end of game :(");
                            isGameNow = false;
                        }
                        break;
                    }
                    case (ACLMessage.REFUSE): case (ACLMessage.FAILURE): case (ACLMessage.NOT_UNDERSTOOD): case (ACLMessage.AGREE): {
                        log("Get %s from %s to %s request", MsgUtil.getStringPerformative(message),
                                message.getSender().getLocalName(), extractConceptFromMessage(message).getClass().getSimpleName());
                        ACLMessage tribeMessage = createMessage(message.getPerformative(), tribeAID);
                        sendActionTo(tribeAID, tribeMessage, (AgentAction) extractConceptFromMessage(message));

                        break;
                    }
                }
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }

    }

    private class FindWorldBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            worldAID = findAgentByServiceType(myAgent, SERVICE_TYPE_WORLD);
        }
    }

    private class TriggerCreateUnitBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            try {
                ACLMessage createUnitMessage = createMessage(ACLMessage.REQUEST, worldAID);
                createUnitMessage.setProtocol(GameOntology.CREATEUNIT);
                CreateUnit createUnit = new CreateUnit();
                sendActionTo(worldAID, createUnitMessage, createUnit);
                log("Unit creation request to %s from position [%d,%d]", worldAID.getLocalName(),
                        position.getX(), position.getY());
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }

    }

    private class TriggerMoveToCellBehaviour extends OneShotBehaviour {

        private int direction;

        TriggerMoveToCellBehaviour(int direction) {
            this.direction = direction;
        }

        @Override
        public void action() {
            try {
                ACLMessage moveToCellMessage = createMessage(ACLMessage.REQUEST, worldAID);
                moveToCellMessage.setProtocol(GameOntology.MOVETOCELL);
                MoveToCell moveToCell = new MoveToCell();
                moveToCell.setTargetDirection(direction);
                sendActionTo(worldAID, moveToCellMessage, moveToCell);
                log("MoveToCell request to %s with direction = %d", worldAID.getLocalName(), direction);
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }

    }

    private class TriggerCreateBuildingBehavior extends OneShotBehaviour {

        private String buildingType;

        TriggerCreateBuildingBehavior(String buildingType) {
            this.buildingType = buildingType;
        }

        @Override
        public void action() {
            try {
                ACLMessage createBuildingMessage = createMessage(ACLMessage.REQUEST, worldAID);
                createBuildingMessage.setProtocol(GameOntology.CREATEBUILDING);
                CreateBuilding createBuilding = new CreateBuilding();
                createBuilding.setBuildingType(buildingType);
                sendActionTo(worldAID, createBuildingMessage, createBuilding);
                log("CreateBuilding request with type %s to %s", buildingType, worldAID.getLocalName());
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }
    }

    private class TriggerExploitResourceBehavior extends OneShotBehaviour {

        @Override
        public void action() {
            try {
                ACLMessage exploitResourseMessage = createMessage(ACLMessage.REQUEST, worldAID);
                exploitResourseMessage.setProtocol(GameOntology.EXPLOITRESOURCE);
                ExploitResource exploitResource = new ExploitResource();
                sendActionTo(worldAID, exploitResourseMessage, exploitResource);
                log("Exploit resource request to %s from position [%d,%d]", worldAID.getLocalName(),
                        position.getX(), position.getY());
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }
    }

}