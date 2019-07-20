package es.upm.woa.group4.agent;

import static es.upm.woa.group4.common.Constants.*;

import java.io.InputStream;
import java.util.*;

import com.google.gson.Gson;
import es.upm.woa.group4.common.Config;
import es.upm.woa.group4.common.Constants;
import es.upm.woa.group4.model.Tribe;
import es.upm.woa.group4.model.Unit;
import es.upm.woa.group4.protocol.InitiateTribes;
import es.upm.woa.group4.util.*;
import es.upm.woa.ontology.*;
import jade.content.AgentAction;
import jade.content.Concept;
import jade.content.lang.Codec.CodecException;
import jade.content.onto.OntologyException;
import jade.content.onto.UngroundedException;
import jade.core.AID;
import jade.core.Agent;
import jade.core.NotFoundException;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.StaleProxyException;

public class AgWorld extends AgBase {

    private static final String TAG = "World";

    private List<Cell> map = new ArrayList<>();
    private List<AID> busyUnits = new ArrayList<>();             // busy units (moving, creating, exploiting, farming etc)
    private List<Cell> busyCells = new ArrayList<>();           // busy cells (creating new buildings)
    private List<Tribe> tribes = new ArrayList<>();
    private HashMap<AID, Integer> tribeScore = new HashMap<>();
    private HashMap<AID, List<Unit>> tribeUnits = new HashMap<>();
    private HashMap<AID, List<Cell>> tribeKnownCells = new HashMap<>();
    private HashMap<Cell, Integer> exploitingCell = new HashMap<>();
    private Boolean isGameOver = false;
    private Integer mapHeight;
    private Integer mapWidth;

    @Override
    protected String getServiceType() {
        return SERVICE_TYPE_WORLD;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void setup() {
        super.setup();
        addBehaviour(new ReceiveMessageBehaviour());

        // let's add some cells representing the possible map given to AgWorld
        createMap();
    }

    private class EndGameBehavior extends WakerBehaviour {

        public EndGameBehavior(Agent agent, long timeout) {
            super(agent,timeout);
        }

        @Override
        protected void handleElapsedTimeout() {
            try {
                isGameOver = true;

                EndOfGame endOfGame = new EndOfGame();
                ACLMessage message = createMessage(ACLMessage.INFORM, null);
                message.clearAllReceiver();
                message.setProtocol(GameOntology.ENDOFGAME);
                for (Tribe tribe : tribes) {
                    message.addReceiver(tribe.getAID());
                }
                sendActionTo(this.myAgent.getAID(),message,endOfGame);

                // Make api call
                Map<String, Object> body = new HashMap<>();
                callApi("/end", body);

                addBehaviour(new ShowFinalScoreBehavior(myAgent));
            } catch (CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
        }
    }

    private class ShowFinalScoreBehavior extends OneShotBehaviour {

        public  ShowFinalScoreBehavior(Agent agent) {
            super(agent);
        }

        @Override
        public void action() {
            // print the table headings
            String header = "--------------------------------------------------\n-----GAME IS OVER. FINAL SCORE-----\n--------------------------------------------------\n";
            //System.out.println(header);
            String messageHeadings = "\tTribe\t\tCells\tCities\tStores\tFarms\tUnits\tGold\tStone\tWood\tFood\t\tSCORE\n";
            //System.out.println(messageHeadings);

            String result = header + messageHeadings;
            String message;

            // print the summary for each tribe
            for (Tribe tribe : tribes) {
                String messageFormat = "\t%s\t\t%5d\t%6d\t%6d\t%5d\t%5d\t%4d\t%5d\t%4d\t%4d\t\t%5d\n";
                message = String.format(messageFormat, tribe.getAID().getLocalName(),
                        tribe.getCellsExplored(), tribe.getCities(),
                        tribe.getStores(), tribe.getFarms(),
                        tribe.getUnits(), tribe.getGold(), tribe.getStone(), tribe.getWood(), tribe.getFood(),
                        calculateTribeScore(tribe));
                //System.out.println(message);
                result += message;
            }

            System.out.printf(result);
        }
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
                        addBehaviour(new CreateUnitBehaviour(myAgent, message));
                    } else if (concept instanceof MoveToCell) {
                        addBehaviour(new MoveToCellBehaviour(myAgent, message));
                    } else if (concept instanceof CreateBuilding) {
                        addBehaviour(new CreateNewBuildingBehavior(myAgent, message));
                    } else if (concept instanceof InitiateTribes) {
                        InitiateTribes initiateTribes = (InitiateTribes) concept;
                        List<AID> l = (List<AID>) ListUtil.castJadeListToJavaList(initiateTribes.getTribeList());
                        addBehaviour(new InitiateTribesBehavior(myAgent, message, l));
                    } else if (concept instanceof ExploitResource) {
                        addBehaviour(new ExploitResourceBehavior(myAgent,message));
                    }
                    break;
                }
                case (ACLMessage.REFUSE): case (ACLMessage.FAILURE): case (ACLMessage.NOT_UNDERSTOOD): case (ACLMessage.AGREE): {
                    log("Get %s from %s to %s request", MsgUtil.getStringPerformative(message),
                            message.getSender().getLocalName(), extractConceptFromMessage(message).getClass().getSimpleName());
                    break;
                }
                }
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }

    }

    private class InitiateTribesBehavior extends OneShotBehaviour {

        ACLMessage message;
        List<AID> tribesRegistered;

        InitiateTribesBehavior(Agent agent, ACLMessage message, List<AID> tribes) {
            super(agent);
            this.message = message;
            this.tribesRegistered = tribes;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void action() {
            try {
                log("Got initialize tribes request from %s", message.getSender().getLocalName());

                // initialize every tribe one by one
                // the map sizes WOULD BE READ IN ANOTHER METHOD as well as STARTING POSITIONS of tribes
                // and other information as Default resources etc

                // Read initial values from map json
                Map<String, Object> map = readMap();
                List<Map<String, Object>> initialPositions = (List<Map<String, Object>>)map.get("initialPositions");
                Map<String, Object> initialResources = (Map<String, Object>)map.get("initialResources");
                Integer initialFood = ((Double)initialResources.get("food")).intValue();
                Integer initialGold = ((Double)initialResources.get("gold")).intValue();
                Integer initialStone = ((Double)initialResources.get("stone")).intValue();
                Integer initialWood = ((Double)initialResources.get("wood")).intValue();

                for (AID tribeAID : tribesRegistered) {
                    // initialize ResourceAccount
                    ResourceAccount resourceAccount = new ResourceAccount();
//                    resourceAccount.setGold(TRIBE_DEFAULT_GOLD);
//                    resourceAccount.setFood(TRIBE_DEFAULT_FOOD);
//                    resourceAccount.setStone(TRIBE_DEFAULT_STONE);
//                    resourceAccount.setWood(TRIBE_DEFAULT_WOOD);
                    resourceAccount.setGold(initialGold);
                    resourceAccount.setFood(initialFood);
                    resourceAccount.setStone(initialStone);
                    resourceAccount.setWood(initialWood);

                    // initialize tribe as a class
                    Tribe tribe = new Tribe(tribeAID, resourceAccount, TRIBE_DEFAULT_CELLS, TRIBE_DEFAULT_UNITS, INITIAL_STORAGE_CAPACITY);

                    // Get random initial position
                    Map<String, Object> initialPosition = initialPositions.get(RandomUtil.betweenNumbers(0, initialPositions.size()));
                    Integer initialPositionX = ((Double)initialPosition.get("x")).intValue();
                    Integer initialPositionY = ((Double)initialPosition.get("y")).intValue();

                    // initialize tribe starting position
                    // now we are doing it hardcoded based on map created in setup method
                    Cell startingPosition = new Cell();
                    startingPosition.setX(initialPositionX);
                    startingPosition.setY(initialPositionY);
                    startingPosition.setContent(getCellContent(startingPosition));
                    List<Cell> knownCells = new ArrayList<>();
                    knownCells.add(startingPosition);

                    // pack everything into InitializeTribe action
                    InitalizeTribe initalizeTribe = new InitalizeTribe();
                    initalizeTribe.setStartingPosition(startingPosition);
                    initalizeTribe.setStartingResources(resourceAccount);
                    initalizeTribe.setInitialStorageCapacity(INITIAL_STORAGE_CAPACITY);
                    initalizeTribe.setStorageCapacityUpgrade(STORAGE_CAPACITY_UPGRADE);
                    initalizeTribe.setMapHeight(mapHeight);
                    initalizeTribe.setMapWidth(mapWidth);

                    // initialize units
                    AID unitAID;
                    List<Unit> units = new ArrayList<>();
                    tribeUnits.put(tribeAID,units);
                    for (int i = 0; i < TRIBE_DEFAULT_UNITS; i++) {
                        unitAID = startAndStoreUnitForTribe(tribeAID, startingPosition);
                        initalizeTribe.addUnitList(unitAID);
                    }

                    // store everything in AgWorld
                    tribes.add(tribe);
                    tribeUnits.put(tribeAID, units);
                    tribeKnownCells.put(tribeAID, knownCells);
                    tribeScore.put(tribeAID, calculateTribeScore(tribe));

                    logDebug("Tribes array size = %d; for %s have %d units, %d cells and %d score", tribes.size(),
                            tribeAID.getLocalName(), tribeUnits.get(tribeAID).size(),
                            tribeKnownCells.get(tribeAID).size(), tribeScore.get(tribeAID));

                    //
                    // send initial data to corresponded tribe
                    ACLMessage initalizeTribeMessage = createMessage(ACLMessage.INFORM, tribeAID);
                    initalizeTribeMessage.setProtocol(GameOntology.INITALIZETRIBE);
                    sendActionTo(tribeAID,initalizeTribeMessage,initalizeTribe);
                }

                // start a game
                addBehaviour(new EndGameBehavior(myAgent,DURATION_MATCH));
            } catch (OntologyException | CodecException e) {
                e.printStackTrace();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }
    }

    private class CreateUnitBehaviour extends OneShotBehaviour {

        private ACLMessage message;
        private AgentAction replyAction;

        public CreateUnitBehaviour(Agent agent, ACLMessage message) {
            super(agent);
            this.message = message;
        }

        @Override
        public void action() {
            try {
                replyAction = (CreateUnit) extractConceptFromMessage(message);

                // store senderAID
                AID senderAID = message.getSender();

                // check whether sender is a unit with a tribe
                AID tribeAID = findTribeByUnit(senderAID);
                if (tribeAID == null) {
                    sendRefuseReply(message,replyAction);
                    return;
                }

                Unit currentUnit = findUnit(senderAID);
                if (currentUnit == null) {
                    sendRefuseReply(message,replyAction);
                    return;
                }
                Cell senderCell = currentUnit.getLocation();

                // Log request
                log("Got unit creation request from %s stood at [%d,%d]", senderAID.getLocalName(),
                        senderCell.getX(), senderCell.getY());

                // check if unit is busy now
                if (isUnitBusy(senderAID)) {
                    sendRefuseReply(message,replyAction);
                    return;
                }

                // Find current tribe
                Tribe currentTribe = findTribe(tribeAID);

                // Log picked tribe
                logDebug("Current tribe is %s", tribeAID.getLocalName());

                // check whether the cell contains Town Hall and this Town Hall belongs to
                // currentTribe
                Object cellContent = getCellContent(senderCell);
                if (!((cellContent instanceof Building) && ((Building) cellContent).getType().equalsIgnoreCase(CONCEPT_TOWN_HALL))) {
                    log("%s is not at Town Hall", senderAID.getLocalName());
                    //sendRefuseReply(message, "Unit is not at Town Hall");
                    sendRefuseReply(message,replyAction);
                    return;
                } else if (!AIDUtil.equals(((Building) cellContent).getOwner(), tribeAID)) {
                    log("%s is not at his tribe's Town Hall", senderAID.getLocalName());
                    //sendRefuseReply(message, "Unit is not at his tribe's Town Hall");
                    sendRefuseReply(message,replyAction);
                    return;
                }

                // Check if tribe has enough resources
                if (currentTribe.getGold() < PRICE_UNIT_GOLD || currentTribe.getFood() < PRICE_UNIT_FOOD) {
                    log("%s does not have enough resources: Gold: %d Food: %d", tribeAID.getLocalName(),
                            currentTribe.getGold(), currentTribe.getFood());
                    //sendRefuseReply(message, "Not enough resources for new unit creation");
                    sendRefuseReply(message,replyAction);
                    return;
                } else {
                    log("%s has following resources: Gold: %d Food: %d", tribeAID.getLocalName(), currentTribe.getGold(),
                            currentTribe.getFood());
                }

                // Send agree reply
                sendAgreeReply(message, replyAction);

                // Consume tribe resources
                currentTribe.setFood(currentTribe.getFood() - PRICE_UNIT_FOOD);
                currentTribe.setGold(currentTribe.getGold() - PRICE_UNIT_GOLD);
                //calculate score
                Integer score = calculateTribeScore(currentTribe);
                tribeScore.put(tribeAID, score);
                log("%s now has remaining resources: Gold: %d Food: %d. It's score = %d", tribeAID.getLocalName(), currentTribe.getGold(),
                        currentTribe.getFood(), score);

                // Schedule creation
                addBehaviour(
                        new DeferCreateUnitBehaviour(myAgent, TIMEOUT_UNIT_CREATION, message, tribeAID, senderCell));
            } catch (NotFoundException e) {
                e.printStackTrace();
            } catch (UngroundedException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            } catch (CodecException e) {
                e.printStackTrace();
            }
        }

    }

    private class DeferCreateUnitBehaviour extends WakerBehaviour {

        private AID tribeAID;
        private ACLMessage message;
        private Cell location;

        public DeferCreateUnitBehaviour(Agent agent, long timeout, ACLMessage message, AID tribeAID, Cell location) {
            super(agent, timeout);
            this.tribeAID = tribeAID;
            this.message = message;
            this.location = location;
            logDebug("Unit is going to be created at [%d,%d]", location.getX(), location.getY());
        }

        @Override
        protected void handleElapsedTimeout() {
            try {
                // Check first if game is over
                if (isGameOver()) {
                    // Send failure
                    sendFailureReply(message,(CreateUnit) extractConceptFromMessage(message));
                    return;
                }

                // Start unit agent
                AID unitAID = startAndStoreUnitForTribe(tribeAID, location);

                Tribe tribe = findTribe(tribeAID);
                // update the units_owned value for this tribe
                tribe.setUnits(tribe.getUnits()+1);

                //calculate score
                Integer score = calculateTribeScore(tribe);
                tribeScore.put(tribeAID, score);
                // make log
                log("Unit of %s was created at [%d,%d]. Now its tribe has %d units owned and its score = %d",
                        tribeAID.getLocalName(),location.getX(), location.getY(), tribe.getUnits(), score);

                // Schedule notify action
                addBehaviour(new NotifyNewUnitBehaviour(myAgent, message, unitAID, location));
            } catch (StaleProxyException e) {
                e.printStackTrace();
            } catch (NotFoundException e) {
                e.printStackTrace();
            } catch (UngroundedException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            } catch (CodecException e) {
                e.printStackTrace();
            }
        }

    }

    private class NotifyNewUnitBehaviour extends OneShotBehaviour {

        private Cell cell;
        private ACLMessage message;
        private AID unitAID;

        public NotifyNewUnitBehaviour(Agent agent, ACLMessage message, AID unitAID, Cell cell) {
            super(agent);
            this.cell = cell;
            this.message = message;
            this.unitAID = unitAID;
        }

        @Override
        public void action() {
            try {
                // Create notify action
                NotifyNewUnit notifyNewUnit = new NotifyNewUnit();
                notifyNewUnit.setLocation(cell);
                notifyNewUnit.setNewUnit(unitAID);

                // Send inform to unit who made a request for newUnit creation
                ACLMessage notifyNewUnitMessage = createReply(message, ACLMessage.INFORM);
                notifyNewUnitMessage.setProtocol(GameOntology.NOTIFYNEWUNIT);
                sendActionTo(message.getSender(), notifyNewUnitMessage, notifyNewUnit);

                // Find the unit's tribe
                AID tribeAID = findTribeByUnit(message.getSender());

                notifyNewUnitMessage = createMessage(ACLMessage.INFORM, tribeAID);
                notifyNewUnitMessage.setProtocol(GameOntology.NOTIFYNEWUNIT);
                sendActionTo(tribeAID, notifyNewUnitMessage, notifyNewUnit);
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }

    }

    private class MoveToCellBehaviour extends OneShotBehaviour {

        private ACLMessage message;
        private AgentAction replyAction;

        public MoveToCellBehaviour(Agent agent, ACLMessage message) {
            super(agent);
            this.message = message;
        }

        @Override
        public void action() {
            try {
                replyAction = (MoveToCell) extractConceptFromMessage(message);

                // store senderAID
                AID senderAID = message.getSender();

                // Extract request from message
                MoveToCell request = extractRequestFromMessage(message);

                // find unit by AID
                Unit unit = findUnit(senderAID);
                if (unit == null) {
                    sendRefuseReply(message,replyAction);
                    return;
                }

                Cell senderCell = unit.getLocation();

                // Log request
                log("Got move to cell request from %s stood at [%d,%d] with direction = %d", senderAID.getLocalName(),
                        senderCell.getX(), senderCell.getY(), request.getTargetDirection());

                // check if unit is busy by doing sth
                if (isUnitBusy(senderAID)) {
                    // Refure action if cell is busy
                    sendRefuseReply(message,replyAction);
                    return;
                }

                // get the direction from request
                int direction = request.getTargetDirection();
                // we are using relational positioning, so let's calculate it
                Cell targetCell = calculateTargetCell(senderCell, direction);
                // check if direction code was in range [1;6]
                if ( targetCell == null ) {
                    //sendRefuseReply(message,"Direction code is invalid");
                    sendRefuseReply(message,replyAction);
                    return;
                }
                targetCell.setContent(getCellContent(targetCell));

                // mark unis as busy until he finishes his movement
                markUnitAsBusy(senderAID, true);
                // Send agree reply
                sendAgreeReply(message, replyAction);
                // Schedule movement
                addBehaviour(
                        new DeferMoveToCellBehaviour(myAgent, message, TIMEOUT_UNIT_MOVEMENT, senderAID, targetCell));
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }

    }

    private class DeferMoveToCellBehaviour extends WakerBehaviour {

        private ACLMessage message;
        private AID senderAID;
        private Cell targetCell;

        public DeferMoveToCellBehaviour(Agent agent, ACLMessage message, long timeout, AID senderAID, Cell targetCell) {
            super(agent, timeout);
            this.message = message;
            this.senderAID = senderAID;
            this.targetCell = targetCell;
        }

        @Override
        protected void handleElapsedTimeout() {
            try {
                // Check first if game is over
                if (isGameOver()) {
                    // Send failure
                    sendFailureReply(message,(MoveToCell) extractConceptFromMessage(message));
                    return;
                }

                Cell senderCell = findUnit(senderAID).getLocation();

                // Release unit - the movement has been finished
                markUnitAsBusy(senderAID, false);
                // update unit's location
                findUnit(senderAID).setLocation(targetCell);

                // set the content of cell
                targetCell.setContent(findCellInList(map,targetCell).getContent());

                // send MoveToCell INFORM to requesting unit
                ACLMessage notifyMoveToCellMessage = createReply(message,ACLMessage.INFORM);
                notifyMoveToCellMessage.setProtocol(GameOntology.MOVETOCELL);
                MoveToCell moveToCell = new MoveToCell();
                moveToCell.setNewlyArrivedCell(targetCell);
                sendActionTo(senderAID, notifyMoveToCellMessage, moveToCell);

                // Make api call
                Map<String, Object> tile = new HashMap<>();
                tile.put("x", targetCell.getX());
                tile.put("y", targetCell.getY());
                Map<String, Object> body = new HashMap<>();
                body.put("agent_id", senderAID.getLocalName());
                body.put("tile", tile);
                callApi("/agent/move", body);

                // Update known cells
                addBehaviour(new UpdateKnownCellsList(myAgent, this.message, senderAID, targetCell));
            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }

    }

    private class UpdateKnownCellsList extends OneShotBehaviour {

        private Cell knownCell;
        private AID unitAID;
        private ACLMessage message;

        public UpdateKnownCellsList(Agent agent, ACLMessage message, AID unitAID, Cell knownCell) {
            super(agent);
            this.knownCell = knownCell;
            this.unitAID = unitAID;
            this.message = message;
        }

        @Override
        public void action() {
            try {
                // find the tribe for the unit gone to new cell
                AID tribeAID = findTribeByUnit(unitAID);
                // check the existance of NewCell explored by a unit in the list of tribe's
                // known cells
                if (isCellKnownByTribe(knownCell, tribeAID)) {
                    logDebug("A cell is already known by tribe %s", tribeAID.getLocalName());
                    return;
                }

                // store the new explored cell in the list of tribe's known cells
                List<Cell> knownCells = tribeKnownCells.get(tribeAID);
                knownCells.add(knownCell);
                tribeKnownCells.put(tribeAID, knownCells);

                // Update the number of cells_explored by this tribe
                Tribe tribe = findTribe(tribeAID);
                tribe.setCellsExplored(tribe.getCellsExplored() + 1);

                //calculate score
                Integer score = calculateTribeScore(tribe);
                tribeScore.put(tribeAID, score);

                // write a log
                log("A new Cell [%d,%d] was explored by %s of %s. Not it has %d cells explored and score = %d", knownCell.getX(), knownCell.getY(),
                        unitAID.getLocalName(), tribeAID.getLocalName(), tribe.getCellsExplored(), score);

                addBehaviour(new NotifyNewExploredCellBehavior(myAgent, message, tribeAID, knownCell));

            } catch (NotFoundException e) {
                e.printStackTrace();
                sendFailureReply(message);
            }
        }
    }

    private class NotifyNewExploredCellBehavior extends OneShotBehaviour {

        private Cell newCell;
        private AID tribeAID;
        private ACLMessage message;

        public NotifyNewExploredCellBehavior(Agent agent, ACLMessage message, AID tribeAID, Cell newCell) {
            super(agent);
            this.newCell = newCell;
            this.tribeAID = tribeAID;
            this.message = message;
        }

        @Override
        public void action() {
            try {

                // Create notifyCellDetail action
                NotifyCellDetail notifyCellDetail = new NotifyCellDetail();
                notifyCellDetail.setNewCell(newCell);

                // inform the tribe about the content of new cell explored by its unit
                ACLMessage notifyNewExploredCellMessage = createMessage(ACLMessage.INFORM, tribeAID);
                notifyNewExploredCellMessage.setProtocol(GameOntology.NOTIFYCELLDETAIL);
                sendActionTo(tribeAID, notifyNewExploredCellMessage, notifyCellDetail);

                // inform every unit of tribe about the content of new cell explored by one of
                for (Unit unit : tribeUnits.get(tribeAID)) {
                    notifyNewExploredCellMessage.clearAllReceiver();
                    notifyNewExploredCellMessage.addReceiver(unit.getAid());
                    sendActionTo(unit.getAid(), notifyNewExploredCellMessage, notifyCellDetail);
                }

            } catch (CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }
    }

    private class CreateNewBuildingBehavior extends OneShotBehaviour {

        private ACLMessage message;
        private AgentAction replyAction;

        public CreateNewBuildingBehavior(Agent agent, ACLMessage message) {
            super(agent);
            this.message = message;
        }

        @Override
        public void action() {
            try {
                replyAction = (CreateBuilding) extractConceptFromMessage(message);

                // store senderAID
                AID senderAID = message.getSender();
                String buildingType = ((CreateBuilding) extractConceptFromMessage(message)).getBuildingType();

                Unit currentUnit = findUnit(senderAID);
                if (currentUnit == null) {
                    sendRefuseReply(message,replyAction);
                    return;
                }
                Cell senderCell = currentUnit.getLocation();

                // Log request
                log("Got building creation request from %s stood at [%d,%d] with building type - %s", senderAID.getLocalName(),
                        senderCell.getX(), senderCell.getY(), buildingType);

                // check if unit is busy buy doing sth or that CELL is busy by somebody else creating a building there or town hall in contiguous cell
                if (isUnitBusy(senderAID) || isCellBusy(senderCell)) {
                    sendRefuseReply(message,replyAction);
                    return;
                }

                senderCell.setContent(getCellContent(senderCell));

                //
                //
                // check conditions for creating new Town Hall OR Store OR Farm
                // first check the content of cell
                if (!(senderCell.getContent() instanceof Ground)) {
                    String messageFormat = "Cell is not plain ground, it is %s";
                    String msg = String.format(messageFormat, senderCell.getContent().getClass().getSimpleName());
                    sendRefuseReply(message,replyAction);
                    return;
                }


                //
                // check the content of contiguous cells
                // and resources available for that
                Cell buildingContiguousCell = null;
                Tribe currentTribe = findTribe(findTribeByUnit(message.getSender()));
                // 1. Town Hall
                switch (buildingType) {
                    case CONCEPT_TOWN_HALL:
                        buildingContiguousCell = checkContiguousCells(senderCell,true, null);
                        if (buildingContiguousCell != null) {
                            sendRefuseReply(message,replyAction);
                            return;
                        }
                        // check the resources of tribe available for creating Town Hall
                        if (!checkResourcesForBuildingCreation(currentTribe,PRICE_TOWN_HALL_GOLD, PRICE_TOWN_HALL_WOOD, PRICE_TOWN_HALL_STONE)) {
                            sendRefuseReply(message,replyAction);
                            return;
                        }
                        break;
                    case CONCEPT_STORE:
                        buildingContiguousCell = checkContiguousCells(senderCell,false, findTribeByUnit(senderAID));
                        if (buildingContiguousCell == null) {
                            sendRefuseReply(message,replyAction);
                            return;
                        }
                        // check the resources of tribe available for creating Store
                        if (!checkResourcesForBuildingCreation(currentTribe,PRICE_STORE_GOLD, PRICE_STORE_WOOD, PRICE_STORE_STONE)) {
                            sendRefuseReply(message,replyAction);
                            return;
                        }
                        break;
                    case CONCEPT_FARM:
                        buildingContiguousCell = checkContiguousCells(senderCell,false, findTribeByUnit(senderAID));
                        if (buildingContiguousCell == null) {
                            sendRefuseReply(message,replyAction);
                            return;
                        }
                        // check the resources of tribe available for creating Store
                        if (!checkResourcesForBuildingCreation(currentTribe,PRICE_FARM_GOLD, PRICE_FARM_WOOD, PRICE_FARM_STONE)) {
                            sendRefuseReply(message,replyAction);
                            return;
                        }
                        break;
                }

                //
                //

                // mark both unit and cell as busy
                markCellAsBusy(senderCell, true);
                markUnitAsBusy(senderAID, true);

                // block adjacent cells if creating TowhHall
                if (buildingType.equalsIgnoreCase(CONCEPT_TOWN_HALL))
                    blockContiguousCellsTownHall(senderCell,true);

                sendAgreeReply(message, replyAction);

                // Make api call
                Map<String, Object> body = new HashMap<>();
                body.put("player_id", currentTribe.getAID().getLocalName());
                body.put("agent_id", senderAID.getLocalName());

                // Consume tribe resources
                switch (buildingType) {
                    case CONCEPT_TOWN_HALL:
                        currentTribe.setWood(currentTribe.getWood() - PRICE_TOWN_HALL_WOOD);
                        currentTribe.setGold(currentTribe.getGold() - PRICE_TOWN_HALL_GOLD);
                        currentTribe.setStone(currentTribe.getStone() - PRICE_TOWN_HALL_STONE);

                        body.put("resource", "wood");
                        body.put("amount", PRICE_TOWN_HALL_WOOD);
                        callApi("/resource/lose", body);

                        body.put("resource", "gold");
                        body.put("amount", PRICE_TOWN_HALL_GOLD);
                        callApi("/resource/lose", body);

                        body.put("resource", "stone");
                        body.put("amount", PRICE_TOWN_HALL_STONE);
                        callApi("/resource/lose", body);
                        break;
                    case CONCEPT_STORE:
                        currentTribe.setWood(currentTribe.getWood() - PRICE_STORE_WOOD);
                        currentTribe.setGold(currentTribe.getGold() - PRICE_STORE_GOLD);
                        currentTribe.setStone(currentTribe.getStone() - PRICE_STORE_STONE);

                        body.put("resource", "wood");
                        body.put("amount", PRICE_STORE_WOOD);
                        callApi("/resource/lose", body);

                        body.put("resource", "gold");
                        body.put("amount", PRICE_STORE_GOLD);
                        callApi("/resource/lose", body);

                        body.put("resource", "stone");
                        body.put("amount", PRICE_STORE_STONE);
                        callApi("/resource/lose", body);
                        break;
                    case CONCEPT_FARM:
                        currentTribe.setWood(currentTribe.getWood() - PRICE_FARM_WOOD);
                        currentTribe.setGold(currentTribe.getGold() - PRICE_FARM_GOLD);
                        currentTribe.setStone(currentTribe.getStone() - PRICE_FARM_STONE);

                        body.put("resource", "wood");
                        body.put("amount", PRICE_FARM_WOOD);
                        callApi("/resource/lose", body);

                        body.put("resource", "gold");
                        body.put("amount", PRICE_FARM_GOLD);
                        callApi("/resource/lose", body);

                        body.put("resource", "stone");
                        body.put("amount", PRICE_FARM_STONE);
                        callApi("/resource/lose", body);
                        break;
                }

                //calculate score
                Integer score = calculateTribeScore(currentTribe);
                tribeScore.put(currentTribe.getAID(), score);
                log("%s now has remaining resources: Gold: %d Wood: %d Stone: %d. Its score = %d", currentTribe.getAID().getLocalName(),
                        currentTribe.getGold(), currentTribe.getWood(), currentTribe.getStone(), score);

                switch (buildingType) {
                    case CONCEPT_TOWN_HALL:
                        addBehaviour(new DeferCreateNewBuildingBehavior(myAgent, TIMEOUT_TOWN_HALL_CREATION, message,
                                buildingType, senderCell));
                        break;
                    case CONCEPT_STORE:
                        addBehaviour(new DeferCreateNewBuildingBehavior(myAgent, TIMEOUT_STORE_CREATION, message,
                                buildingType, senderCell));
                        break;
                    case CONCEPT_FARM:
                        addBehaviour(new DeferCreateNewBuildingBehavior(myAgent, TIMEOUT_FARM_CREATION, message,
                                buildingType, senderCell));
                        break;
                }
            } catch (UngroundedException e) {
                e.printStackTrace();
            } catch (OntologyException | CodecException e) {
                e.printStackTrace();
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private class DeferCreateNewBuildingBehavior extends WakerBehaviour {

        ACLMessage message;
        String buildingType;
        Cell cell;

        DeferCreateNewBuildingBehavior(Agent agent, long timeout, ACLMessage message, String buildingType, Cell cell) {
            super(agent, timeout);
            this.message = message;
            this.buildingType = buildingType;
            this.cell = cell;
        }

        @Override
        protected void handleElapsedTimeout() {
            try {
            // Check first if game is over
            if (isGameOver()) {
                // Send failure
                sendFailureReply(message, (CreateBuilding) extractConceptFromMessage(message));
                return;
            }

            AID tribeAID = findTribeByUnit(message.getSender());

            // release both unit and cell
            markCellAsBusy(cell,false);
            markUnitAsBusy(message.getSender(),false);
            // unblock adjacent cells if creating TowhHall
            if (buildingType.equalsIgnoreCase(CONCEPT_TOWN_HALL))
                blockContiguousCellsTownHall(cell,false);

            Building building = new Building();
            building.setType(buildingType);
            building.setOwner(tribeAID);
            findCellInList(map,cell).setContent(building);
            cell.setContent(building);

            // update the number of building the tribe has now
            Tribe tribe = findTribe(tribeAID);
            int newAmount;
            switch (buildingType) {
                case CONCEPT_TOWN_HALL:
                    // update the number of cities_owned by this tribe
                    tribe.setCities(tribe.getCities() + 1);
                    newAmount = tribe.getCities();
                    break;
                case CONCEPT_FARM:
                    // update the number of farms_owned by this tribe
                    tribe.setFarms(tribe.getFarms() + 1);
                    newAmount = tribe.getFarms();
                    break;
                case CONCEPT_STORE:
                    // update the number of stores_owned by this tribe and its storage_capacity
                    tribe.setStores(tribe.getStores() + 1);
                    tribe.setStorageCapacity(tribe.getStorageCapacity() + STORAGE_CAPACITY_UPGRADE);
                    newAmount = tribe.getStores();
                    break;
                 default:
                    newAmount = 0;
            }

            //calculate score
            Integer score = calculateTribeScore(tribe);
            tribeScore.put(tribeAID, score);
            // make log
            String newType = (buildingType.equalsIgnoreCase(CONCEPT_TOWN_HALL))
                    ? "cities"
                    : ((buildingType.equalsIgnoreCase(CONCEPT_FARM) ? "farms" : "stores"));
            log("New %s is created in cell [%d,%d] by %s. Now it has %d %s and its score = %d",
                    buildingType, cell.getX(), cell.getY(), tribeAID.getLocalName(), newAmount, newType, score);

            // Make api call
            Map<String, Object> body = new HashMap<>();
            body.put("agent_id", message.getSender().getLocalName());
            body.put("type", buildingType);
            callApi("/building/create", body);

            addBehaviour(new NotifyNewBuildingBehavior(myAgent, message, cell, buildingType));

            } catch (NotFoundException e) {
                e.printStackTrace();
            } catch (UngroundedException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            } catch (CodecException e) {
                e.printStackTrace();
            }
        }
    }

    private class NotifyNewBuildingBehavior extends OneShotBehaviour {

        ACLMessage message;
        String buildingType;
        Cell cell;

        NotifyNewBuildingBehavior(Agent agent, ACLMessage message, Cell cell, String buildingType) {
            super(agent);
            this.message = message;
            this.buildingType = buildingType;
            this.cell = cell;
        }

        @Override
        public void action() {
            try {
                NotifyCellDetail notifyCellDetail = new NotifyCellDetail();
                notifyCellDetail.setNewCell(cell);

                // send the confirmation of creating Town Hall to requester unit
                ACLMessage notifyNewBuildingMessage = createReply(message, ACLMessage.INFORM);
                notifyNewBuildingMessage.setProtocol(GameOntology.NOTIFYCELLDETAIL);
                sendActionTo(message.getSender(), notifyNewBuildingMessage, notifyCellDetail);

                // notify other tribes knowing that cell about the new building
                for (Tribe tribe : tribes) {
                    if (isCellKnownByTribe(cell, tribe.getAID())) {
                        notifyNewBuildingMessage = createMessage(ACLMessage.INFORM, tribe.getAID());
                        notifyNewBuildingMessage.setProtocol(GameOntology.NOTIFYCELLDETAIL);
                        sendActionTo(tribe.getAID(), notifyNewBuildingMessage, notifyCellDetail);
                    }
                }
            } catch (CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
        }
    }

    private class ExploitResourceBehavior extends OneShotBehaviour {

        private ACLMessage message;
        private AgentAction replyAction;

        public ExploitResourceBehavior(Agent agent, ACLMessage message) {
            super(agent);
            this.message = message;
        }

        @Override
        public void action() {
            try {
                replyAction = (ExploitResource) extractConceptFromMessage(message);

                // store senderAID
                AID senderAID = message.getSender();

                Unit currentUnit = findUnit(senderAID);
                if (currentUnit == null) {
                    sendRefuseReply(message,replyAction);
                    return;
                }
                Cell senderCell = currentUnit.getLocation();
                senderCell.setContent(getCellContent(senderCell));

                // Log request
                log("Got exploit resource request from %s stood at [%d,%d] with content - %s", senderAID.getLocalName(),
                        senderCell.getX(), senderCell.getY(),
                        (senderCell.getContent() instanceof Resource) ? ((Resource) senderCell.getContent()).getResourceType() : senderCell.getContent().getClass().getSimpleName());

                // check if unit is busy buy doing sth else
                if (isUnitBusy(senderAID)) {
                    sendRefuseReply(message,replyAction);
                    return;
                }

                // check conditions of mining ore, chopping forest or harvesting the farm
                CellContent content = senderCell.getContent();
                String type = null;
                int blockage = 0;
                if (content instanceof Resource) {
                    type = ((Resource) content).getResourceType();
                    if (((Resource) content).getResourceAmount() <= 0) {
                        // there is no more available resource for chopping or mining
                        sendRefuseReply(message, replyAction);
                        return;
                    }
                    blockage = (((Resource) content).getResourceAmount() < 10) ? ((Resource) content).getResourceAmount() : RESOURCE_AMOUNT_BLOCKAGE;
                    ((Resource) content).setResourceAmount(((Resource) content).getResourceAmount() - blockage);
                    log("Blocked %d of resource amount in %s in cell [%d,%d]", blockage,
                            type, senderCell.getX(), senderCell.getY());

                    // mechanism of accumulating blocked amount
                    if (exploitingCell.containsKey(senderCell)) {
                        exploitingCell.put(senderCell,exploitingCell.get(senderCell)+blockage);
                        log("Cell with %s updated blockage amount from %d to %d",
                                ((Resource) content).getResourceType(), exploitingCell.get(senderCell)-blockage, exploitingCell.get(senderCell));
                    } else {
                        exploitingCell.put(senderCell,blockage);
                        log("New cell with %s put in a list with %d of blockage amount",
                                ((Resource) content).getResourceType(), blockage);
                    }

                } else if (content instanceof Building) {
                    Building building = (Building)content;
                    type = building.getType();
                    // check if the building is Farm
                    if (!type.equalsIgnoreCase(CONCEPT_FARM)) {
                        log("%s sends request for resource exploitation from %s",
                                senderAID.getLocalName(), type);
                        sendRefuseReply(message,replyAction);
                        return;
                    } else {
                        // then check if the Farm belongs to unit's tribe (findTribeByUnit method)
                        AID tribeAID = findTribeByUnit(senderAID);
                        if (building.getOwner() != tribeAID) {
                            // Farm does not belong to tribe
                            log("Farm does not belong to following tribe %s", tribeAID.getLocalName());
                            sendRefuseReply(message, replyAction);
                            return;
                        }
                        blockage = HARVEST_FOOD;
                    }
                } else {
                    // cell is Ground or null
                    sendRefuseReply(message,replyAction);
                    return;
                }


                markUnitAsBusy(senderAID, true);

                sendAgreeReply(message, replyAction);

                // define the type of resource to be exploited
                int timeout = (type.equalsIgnoreCase(CONCEPT_FARM))
                        ? TIMEOUT_FARM_HARVESTING
                        : ((type.equalsIgnoreCase(CONCEPT_FOREST))
                        ? TIMEOUT_FOREST_CHOPPING
                        : TIMEOUT_ORE_MINING);

                // Make api call
                Map<String, Object> body = new HashMap<>();
                body.put("agent_id", senderAID.getLocalName());
                body.put("type", "exploit");
                callApi("/agent/start", body);

                // add behavior below
                addBehaviour(new DeferExploitResourceBehavior(myAgent,timeout, message, blockage, senderCell));
            } catch (UngroundedException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            } catch (CodecException e) {
                e.printStackTrace();
            }
        }
    }

    private class DeferExploitResourceBehavior extends WakerBehaviour {

        ACLMessage message;
        int blockageAmount;
        Cell cell;

        public DeferExploitResourceBehavior(Agent agent, long timeout, ACLMessage message, int blockage, Cell cell) {
            super(agent, timeout);
            this.message = message;
            this.blockageAmount = blockage;
            this.cell = cell;
        }

        @Override
        protected void handleElapsedTimeout() {
            try {
                // Check first if game is over
                if (isGameOver()) {
                    // Send failure
                    sendFailureReply(message,(ExploitResource) extractConceptFromMessage(message));
                    return;
                }

                // release unit
                markUnitAsBusy(message.getSender(),false);

                // update the state of a cell - necessary only for FOREST & ORE, not for FARM
                int blockedAmount = exploitingCell.get(cell);
                if (blockedAmount <= blockageAmount) {
                    exploitingCell.remove(cell);
                } else {
                    exploitingCell.put(cell, exploitingCell.get(cell) - blockageAmount);
                }

                String msg = "";

                // check if the cell gave the last resource
                CellContent content = cell.getContent();
                if (content instanceof Resource) {
                    String msgFormat = "%s exploited %d amount of resource %s; estimated resource amount is %d";
                    if (((Resource) content).getResourceAmount() <= 0 && !exploitingCell.containsKey(cell)) {
                        cell.setContent(new Ground());
                        msgFormat += "; resource depleted --> " + cell.getContent().getClass().getSimpleName();

                        // Make api call
                        Map<String, Object> tile = new HashMap<>();
                        tile.put("x", cell.getX());
                        tile.put("y", cell.getY());
                        Map<String, Object> body = new HashMap<>();
                        body.put("tile", tile);
                        callApi("/resource/deplete", body);
                    } else {
                        msgFormat += "; " + ((((Resource) content).getResourceAmount() > 0) ? "still have some resource" : "not all exploitations are finished");
                    }
                    msg = String.format(msgFormat,message.getSender().getLocalName(), blockageAmount,
                            ((Resource) content).getResourceType(), ((Resource) content).getResourceAmount());
                } else if (content instanceof Building) {
                    // another behavior (of just log) for farm harvesting
                    if (((Building)content).getType().equalsIgnoreCase(CONCEPT_FARM)) {
                        String messageFormat = "%s harvested %d amount of food";
                        msg = String.format(messageFormat, message.getSender().getLocalName(), blockageAmount);
                    }
                }


                // find the tribe
                Tribe tribe = findTribe(findTribeByUnit(message.getSender()));

                // add check if the tribe's capacity is over --> send zero as blockage_amount
                if (calculateTribeCapacity(findTribe(findTribeByUnit(message.getSender()))) < blockageAmount) {
                    blockageAmount = 0;
                    log(msg + "; %s doesn't have enough space in storage to store new resources",
                            tribe.getAID().getLocalName());
                } else {
                    // if tribe has enough place in storage - update resource account & calculate the score
                    if (content instanceof Resource) {
                        if (((Resource) content).getResourceType().equalsIgnoreCase(CONCEPT_FOREST)) {
                            tribe.setWood(tribe.getWood() + blockageAmount);
                        } else {
                            tribe.setGold(tribe.getGold() + (blockageAmount * ((Resource) content).getGoldPercentage() / 100));
                            tribe.setStone(tribe.getStone() + (blockageAmount * (100 - ((Resource) content).getGoldPercentage()) / 100));
                        }
                    } else if (content instanceof Building) {
                        // farm harvesting update resource account here
                        if (((Building)content).getType().equalsIgnoreCase(CONCEPT_FARM)) {
                            tribe.setFood(tribe.getFood() + blockageAmount); // blockage amount should be 5
                        }
                    }
                    log(msg + "; its score = %d", calculateTribeScore(tribe));
                }

                // create duplicate of Cell to send the proper Resource content for notification
                Cell exploitedCell = new Cell();
                exploitedCell.setContent(content);
                exploitedCell.setY(cell.getY());
                exploitedCell.setX(cell.getX());
                // add notification
                addBehaviour(new NotifyExploitResourceBehavior(myAgent,message,blockageAmount,exploitedCell));
            } catch (UngroundedException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            } catch (CodecException e) {
                e.printStackTrace();
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    private class NotifyExploitResourceBehavior extends OneShotBehaviour {

        ACLMessage message;
        int blockageAmount;
        Cell cell;

        public NotifyExploitResourceBehavior(Agent agent, ACLMessage message, int blockageAmount, Cell cell) {
            super(agent);
            this.message = message;
            this.blockageAmount = blockageAmount;
            this.cell = cell;
        }

        @Override
        public void action() {
            try {
                ExploitResource exploitResource = (ExploitResource) extractConceptFromMessage(message);
                CellContent content = cell.getContent();

                // do some actions only if tribe's capacity is not over yet, otherwise send empty protocol
                if(blockageAmount > 0) {
                    if ((content instanceof Resource)) {
                        // if resource is forest - add only one resource
                        if (((Resource) content).getResourceType().equalsIgnoreCase(CONCEPT_FOREST)) {
                            GainedResource forest = new GainedResource();
                            forest.setResourceName(RESOURCE_WOOD);
                            forest.setAmount(blockageAmount);
                            exploitResource.addResourceList(forest);
                        } else { // add two types from mine
                            GainedResource gold = new GainedResource();
                            gold.setResourceName(RESOURCE_GOLD);
                            gold.setAmount(blockageAmount * ((Resource) content).getGoldPercentage() / 100);
                            GainedResource stone = new GainedResource();
                            stone.setResourceName(RESOURCE_STONE);
                            stone.setAmount(blockageAmount * (100 - ((Resource) content).getGoldPercentage()) / 100);
                            exploitResource.addResourceList(gold);
                            exploitResource.addResourceList(stone);
                        }
                    } else if (content instanceof Building) {
                        // check the farm and do food notify
                        if (((Building)content).getType().equalsIgnoreCase(CONCEPT_FARM)) {
                            GainedResource food = new GainedResource();
                            food.setAmount(blockageAmount);
                            food.setResourceName(RESOURCE_FOOD);
                            exploitResource.addResourceList(food);
                        }
                    }
                }

                // Make api call
                Map<String, Object> body;
                for (Iterator<GainedResource> iterator = exploitResource.getAllResourceList(); iterator.hasNext(); ) {
                    GainedResource gainedResource = iterator.next();
                    body = new HashMap<>();
                    body.put("player_id", findTribeByUnit(message.getSender()).getLocalName());
                    body.put("agent_id", message.getSender().getLocalName());
                    body.put("amount", gainedResource.getAmount());
                    if (gainedResource.getResourceName() == RESOURCE_FOOD) {
                        body.put("agent_id", -1);
                        body.put("resource", "food");
                    }
                    else if (gainedResource.getResourceName() == RESOURCE_GOLD) {
                        body.put("resource", "gold");
                    }
                    else if (gainedResource.getResourceName() == RESOURCE_STONE) {
                        body.put("resource", "stone");
                    }
                    else if (gainedResource.getResourceName() == RESOURCE_WOOD) {
                        body.put("resource", "wood");
                    }
                    callApi("/resource/gain", body);
                }

                ACLMessage exploitMessage = createReply(message,ACLMessage.INFORM);
                exploitMessage.setProtocol(GameOntology.EXPLOITRESOURCE);
                sendActionTo(message.getSender(),exploitMessage,exploitResource);
            } catch (UngroundedException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            } catch (CodecException e) {
                e.printStackTrace();
            }
        }
    }

    //
    // METHODS
    //

    @SuppressWarnings("unchecked")
    private void createMap() {
        Map<String, Object> map = readMap();
        mapWidth = ((Double)map.get("mapWidth")).intValue();
        mapHeight = ((Double)map.get("mapHeight")).intValue();
        List<Map<String, Object>> tiles = (List<Map<String, Object>>)map.get("tiles");
        for (Map<String, Object> tile : tiles) {
            Integer x = ((Double)tile.get("x")).intValue();
            Integer y = ((Double)tile.get("y")).intValue();
            String type = tile.get("resource").toString();
            Integer amount = 0;
            Integer goldPercentage = 0;
            if (tile.containsKey("resource_amount")) {
                amount = ((Double)tile.get("resource_amount")).intValue();
            }
            if (tile.containsKey("gold_percentage")) {
                goldPercentage = ((Double)tile.get("gold_percentage")).intValue();
            }
            Cell cell;
            if (type.equalsIgnoreCase(Constants.CONCEPT_GROUND)) {
                cell = CellUtil.createGroundCell(x, y);
            } else {
                cell = CellUtil.createResourceCell(type, goldPercentage, amount, x, y);
            }
            storeCell(cell);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap() {
        String mapPath = Config.getInstance().getMapPath();
        InputStream stream = getClass().getClassLoader().getResourceAsStream(mapPath);
        String mapJson = FileUtil.getContentOfResource(stream);
        Map<String, Object> map = new Gson().fromJson(mapJson, Map.class);
        return map;
    }

    private Cell checkContiguousCells(Cell cell, Boolean isForTownHall, AID ownerAID) {
        Cell checkedCell = null;
        for (int i = 1; i < 7; i++) {
            checkedCell =  calculateTargetCell(cell,i);
            checkedCell.setContent(getCellContent(checkedCell));
            // for TownHall it's necessary to check if there is already any building around or on of cells is busy due to creation of other Town Hall
            if ( (isForTownHall && (checkedCell.getContent() instanceof Building || isCellBusy(checkedCell))) ||
                    (!isForTownHall &&
                            checkedCell.getContent() instanceof Building &&
                            AIDUtil.equals(((Building) checkedCell.getContent()).getOwner(), ownerAID))
                ) {
                return checkedCell;
            }
        }
        return null;
    }

    private CellContent getCellContent(Cell cell) {
        Cell searchCell = findCellInList(map, cell);
        return (searchCell == null) ? null : searchCell.getContent();
    }

    private Cell findCellInList(List<Cell> cells, Cell correspondingCell) {
        for (Cell cell : cells) {
            if (CellUtil.hasSameCoordinates(cell, correspondingCell)) {
                return cell;
            }
        }
        return null;
    }

    private Tribe findTribe(AID tribeAID) throws NotFoundException {
        for (Tribe tribe : tribes) {
            if (AIDUtil.equals(tribe.getAID(), tribeAID)) {
                return tribe;
            }
        }
        String messageFormat = "Tribe is not found for %s";
        String message = String.format(messageFormat, tribeAID.getLocalName());
        throw new NotFoundException(message);
    }

    private AID findTribeByUnit(AID unitAID) {// throws NotFoundException {
        for (AID tribeAID : tribeUnits.keySet()) {
            for (Unit unit : tribeUnits.get(tribeAID)) {
                if (AIDUtil.equals(unit.getAid(), unitAID)) {
                    return tribeAID;
                }
            }
        }
        return null;
    }

    private Unit findUnit(AID unitAID) {
        for (AID tribeAID : tribeUnits.keySet()) {
            for (Unit unit : tribeUnits.get(tribeAID)) {
                if (AIDUtil.equals(unit.getAid(), unitAID)) {
                    return unit;
                }
            }
        }
        return null;
    }

    private Boolean isCellKnownByTribe(Cell newCell, AID tribeAID) {
        for (Cell cell : tribeKnownCells.get(tribeAID)) {
            if (CellUtil.hasSameCoordinates(cell, newCell)) {
                return true;
            }
        }
        return false;
    }

    private String getNextUnitNameForTribe(AID tribeAID) {
        List<Unit> units = tribeUnits.get(tribeAID);
        String unitName = String.format("%s-unit%d", tribeAID.getLocalName(), units.size() + 1);
        return unitName;
    }

    // checks if the cell is busy because another unit is creating a building in or doing town hall in adjacent cell
    // used only when a unit wants to create some buildings
    private Boolean isCellBusy(Cell cell) {
        return busyCells.contains(cell);
    }

    private Boolean isUnitBusy(AID unitAID) {
        return busyUnits.contains(unitAID);
    }

    private Cell calculateTargetCell(Cell senderCell, int direction) {
        return CellUtil.calculateTargetCell(senderCell,direction,mapHeight,mapWidth);
    }

    //
    //

    private Boolean isGameOver() {
        return isGameOver;
    }

    private void markUnitAsBusy(AID unitAID, Boolean busy) {
        if (busy) {
            busyUnits.add(unitAID);
        } else {
            busyUnits.remove(unitAID);
        }
    }

    private void markCellAsBusy(Cell cell, Boolean busy) {
        if (busy && !busyCells.contains(cell)) {
            busyCells.add(cell);
        }
        if (!busy && busyCells.contains(cell)) {
            busyCells.remove(cell);
        }
    }

    private void blockContiguousCellsTownHall(Cell cell, Boolean busy) {
        Cell newCell;
        for (int i = 1; i < 7; i++) {
            newCell = calculateTargetCell(cell,i);
            newCell.setContent(getCellContent(newCell));
            markCellAsBusy(newCell,busy);
        }
    }

    private Boolean checkResourcesForBuildingCreation(Tribe tribe, Integer price_gold, Integer price_wood, Integer price_stone) {
        if (tribe.getGold() < price_gold || tribe.getWood() < price_wood
                || tribe.getStone() < price_stone) {
            log("%s does not have enough resources: Gold: %d Wood: %d Stone: %d",
                    tribe.getAID().getLocalName(), tribe.getGold(), tribe.getWood(),
                    tribe.getStone());
            return false;
        } else {
            log("%s has following resources: Gold: %d Wood: %d Stone: %d", tribe.getAID().getLocalName(),
                    tribe.getGold(), tribe.getWood(), tribe.getStone());
        }
        return true;
    }

    private AID startAndStoreUnitForTribe(AID tribeAID, Cell location) throws StaleProxyException {
        String nextUnitName = getNextUnitNameForTribe(tribeAID);
        AID unitAID = null;
        int teamNumber = Integer.valueOf(tribeAID.getLocalName().substring(tribeAID.getLocalName().length()-1),10);

        switch (teamNumber) {
            case 1:
                unitAID = startAgent(nextUnitName, es.upm.woa.group1.agent.AgUnit.class);
                break;
            case 2:
                unitAID = startAgent(nextUnitName, es.upm.woa.group2.agent.AgUnit.class);
                break;
            case 3:
                unitAID = startAgent(nextUnitName, es.upm.woa.group3.agent.AgUnit.class);
                break;
            case 4: default:
                unitAID = startAgent(nextUnitName, es.upm.woa.group4.agent.AgUnit.class);
                break;
            case 5:
                unitAID = startAgent(nextUnitName, es.upm.woa.group5.agent.AgUnit.class);
                break;
        }

        // Store unit for tribe
        storeUnitForTribe(tribeAID, unitAID, location);
        logDebug("Unit %s created for tribe %s", unitAID.getLocalName(), tribeAID.getLocalName());

        // Make api call
        Map<String, Object> tile = new HashMap<>();
        tile.put("x", location.getX());
        tile.put("y", location.getY());
        Map<String, Object> body = new HashMap<>();
        body.put("player_id", tribeAID.getLocalName());
        body.put("agent_id", unitAID.getLocalName());
        body.put("tile", tile);
        callApi("/agent/create", body);

        // Return unit aid
        return unitAID;
    }

    private void storeCell(Cell cell) {
        map.add(cell);
        CellContent content = cell.getContent();
        if (content instanceof Ground) {
            logDebug("Cell [%d,%d] with 'Ground' type stored in World knowledge", cell.getX(), cell.getY());
        } else if (content instanceof Resource) {
            Resource resource = (Resource)content;
            Integer amount = resource.getResourceAmount();
            String type = resource.getResourceType();
            logDebug("Cell [%d,%d] with '%s' type (Amount: %d) stored in World knowledge", cell.getX(), cell.getY(), type, amount);
        } else {
            logDebug("Cell [%d,%d] stored in World knowledge", cell.getX(), cell.getY());
        }
    }

    private void storeUnitForTribe(AID tribeAID, AID unitAID, Cell location) {
        List<Unit> units = tribeUnits.get(tribeAID);
        Unit newUnit = new Unit();
        newUnit.setAid(unitAID);
        newUnit.setLocation(location);
        units.add(newUnit);
    }

    private Integer calculateTribeCapacity(Tribe tribe) {
        int current_storage = tribe.getStorageCapacity();
        int resource_amount = tribe.getGold() + tribe.getStone() + tribe.getWood() + tribe.getFood();
        return current_storage - resource_amount;
    }

    private Integer calculateTribeScore(Tribe tribe) {
        return 100 * tribe.getCellsExplored() + 500 * tribe.getCities() + 400 * tribe.getUnits()
                + 300 * tribe.getFarms() + 250 * tribe.getStores()
                + 10 * tribe.getGold() + 2 * tribe.getStone() + 1 * tribe.getWood() + 5 * tribe.getFood();
    }

}