package es.upm.woa.group4.agent;

import java.util.*;

import es.upm.woa.group4.protocol.OwnOntology;
import es.upm.woa.group4.protocol.WelcomeUnit;
import es.upm.woa.group4.util.AIDUtil;
import es.upm.woa.group4.util.CellUtil;
import es.upm.woa.group4.util.ListUtil;
import es.upm.woa.group4.util.MsgUtil;
import es.upm.woa.ontology.*;
import jade.content.AgentAction;
import jade.content.Concept;
import jade.content.lang.Codec;
import jade.content.onto.OntologyException;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

import static es.upm.woa.group4.common.Constants.*;

public class AgTribe extends AgBase {

    private static final String TAG = "Tribe";
    private HashMap<AID, Cell> myUnits = new HashMap<>();
    private List<Cell> exploredCells = new ArrayList<>();
    private List<Cell> myBuildings = new ArrayList<>();     // for storing my Town Halls, Stores and Farms
    private List<Cell> enemyBuildings = new ArrayList<>();  // for storing enemies' buildings
    private List<Cell> resources = new ArrayList<>();       // for storing cells with Ore || Forest resources

    //Contains builder as key and belonging Townhall cell as value
    private HashMap<AID, Cell> builderUnits = new HashMap<>();
    //List of busy units that is updated once they finish doing AgentAction
    private HashMap<AID, Concept> busyUnits = new HashMap<>();
    //List of all worker units
    private Set<AID> workerUnits = new HashSet<>();
    //List of units from which we are waiting for response on request
    private Set<AID> unitsInWaiting = new HashSet<>();

    private boolean needToIncreaseCapacity = false;

    private ResourceAccount resourceAccount;
    private int mapWidth, mapHeight;
    private int initialStorageCapacity, storageCapacityUpgrade, currentStorageCapacity;
    private boolean isGameNow = false;
    private int townHallsBuilt = 0;
    private int farms = 0;
    private int stores = 0;

    @Override
    protected String getServiceType() {
        return SERVICE_TYPE_TRIBE;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void setup() {
        super.setup();
        addBehaviour(new ReceiveMessageBehaviour());
        addBehaviour(new SendRegistrationRequest(this));
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
                    case (ACLMessage.INFORM): {
                        // other messages
                        Concept concept = extractConceptFromMessage(message);
                        if (concept instanceof NotifyCellDetail) {
                            NotifyCellDetail notifyCellDetail = (NotifyCellDetail) concept;
                            Cell newCell = notifyCellDetail.getNewCell();
                            CellContent cellContent = newCell.getContent();

                            // let's check if the cell is already known or just explored
                            Cell checkCell = null;
                            for (Cell cell : exploredCells) {
                                if (CellUtil.hasSameCoordinates(cell, newCell)) {
                                    checkCell = cell;
                                    break;
                                }
                            }

                            String msg1 = "";
                            // if we didn't find cell in a list --> cell was just explored by our unit
                            if (checkCell == null) {
//                                log("New cell [%d;%d] was explored with content %s",
//                                        newCell.getX(), newCell.getY(), cellContent.getClass().getSimpleName());
                                msg1 = String.format("New cell [%d;%d] was explored; ",
                                        newCell.getX(), newCell.getY());

                                exploredCells.add(newCell);
                                //return;
                            }

                            String msg2 = "";
                            // some actions with cellContent for strategy if cell was already known
                            if (cellContent instanceof Building) {
                                Building building = (Building) cellContent;
                                if (AIDUtil.equals(building.getOwner(),this.getAgent().getAID())) {
                                    // building was created by my unit
//                                    log("%s was created by the unit of my tribe in cell [%d,%d]",
//                                            building.getType(), newCell.getX(), newCell.getY());
                                    msg2 = String.format("%s was created by my unit in cell [%d,%d]",
                                            building.getType(), newCell.getX(), newCell.getY());
                                    myBuildings.add(newCell);
                                    updateCellContent(newCell, building);
                                    myUnits.put(message.getSender(),newCell);

                                    if (building.getType().equalsIgnoreCase(CONCEPT_STORE)) {
                                        currentStorageCapacity += storageCapacityUpgrade;
                                        msg2 += "; updated my storage capacity to " + currentStorageCapacity;

                                        if (currentStorageCapacity > calculateResources()) {
                                            needToIncreaseCapacity = false;
                                        }
                                    }

                                    addBehaviour(new StrategyActionBehavior(myAgent,false,false,true, true,
                                            false,false,false,false, newCell, ACLMessage.INFORM, null, concept));

                                } else {
                                    // building was created by other tribe's unit on my known territory or I explored new cell with somebody's building
                                    if (checkCell != null) {
//                                        log("%s was created by the unit of another tribe in cell [%d,%d]",
//                                                building.getType(), newCell.getX(), newCell.getY());
                                        msg2 = String.format("%s was created by the unit of another tribe in cell [%d,%d]",
                                                building.getType(), newCell.getX(), newCell.getY());

                                        addBehaviour(new StrategyActionBehavior(myAgent,false,false,true, false,
                                                false,false,false,false, newCell, ACLMessage.INFORM, null,concept));
                                    }
                                    else {
//                                        log("%s belongs to tribe in cell [%d,%d]",
//                                                building.getType(), newCell.getX(), newCell.getY());
                                        msg2 = String.format("%s belongs to tribe in cell [%d,%d]",
                                                building.getType(), newCell.getX(), newCell.getY());

                                        addBehaviour(new StrategyActionBehavior(myAgent,false,true,false, false,
                                                false,false,false,false,newCell, ACLMessage.INFORM, null, concept));
                                    }
                                    enemyBuildings.add(newCell);
                                    updateCellContent(newCell, building);
                                }
                            } else if (cellContent instanceof Resource) {
                                Resource resource = (Resource) cellContent;
                                resources.add(newCell);
                                msg2 = String.format("resource type is %s with %d amount",
                                        resource.getResourceType(), resource.getResourceAmount());
                                addBehaviour(new StrategyActionBehavior(myAgent,false,true,false, false,
                                        false,false,false,false,newCell, ACLMessage.INFORM, null, concept));
                            } else if (cellContent instanceof Ground) {
                                msg2 = String.format("just Ground cell");
                                addBehaviour(new StrategyActionBehavior(myAgent,false,true,false, false,
                                        false,false,false,false,newCell, ACLMessage.INFORM, null, concept));
                            }
                            log(msg1+msg2);
                        } else if (concept instanceof NotifyNewUnit) {
                            NotifyNewUnit notifyNewUnit = (NotifyNewUnit) concept;
                            myUnits.put(notifyNewUnit.getNewUnit(), notifyNewUnit.getLocation());
                            log("%s was created in cell [%d,%d]",
                                    notifyNewUnit.getNewUnit().getLocalName(),
                                    notifyNewUnit.getLocation().getX(),
                                    notifyNewUnit.getLocation().getY());
                            // send welcome message to my initiate unit
                            WelcomeUnit welcomeUnit = new WelcomeUnit();
                            welcomeUnit.setTribeAID(getAID());
                            ACLMessage welcomeUnitMessage = createMessage(ACLMessage.REQUEST, notifyNewUnit.getNewUnit());
                            sendActionTo(notifyNewUnit.getNewUnit(), welcomeUnitMessage, welcomeUnit);

                            addBehaviour(new StrategyActionBehavior(myAgent,false,false,false, false,
                                    true,false,false,false, notifyNewUnit.getLocation(), ACLMessage.INFORM,
                                    notifyNewUnit.getNewUnit(), concept));
                        } else if (concept instanceof InitalizeTribe) {
                            InitalizeTribe initalizeTribe = (InitalizeTribe) concept;

                            resourceAccount = initalizeTribe.getStartingResources();
                            exploredCells.add(initalizeTribe.getStartingPosition());
                            initialStorageCapacity = initalizeTribe.getInitialStorageCapacity();
                            storageCapacityUpgrade = initalizeTribe.getStorageCapacityUpgrade();
                            currentStorageCapacity = initialStorageCapacity;
                            mapWidth = initalizeTribe.getMapWidth();
                            mapHeight = initalizeTribe.getMapHeight();

                            String cellContentType = "Ground";
                            if (initalizeTribe.getStartingPosition().getContent() instanceof Resource) {
                                cellContentType = ((Resource) initalizeTribe.getStartingPosition().getContent()).getResourceType();
                            }

                            log("Received initial resources, starting position [%d,%d] (type %s) and units; mapWidth = %d , mapHeight = %d; initial capacity = %d [upgrade value = %d]",
                                    initalizeTribe.getStartingPosition().getX(), initalizeTribe.getStartingPosition().getY(),
                                    cellContentType,
                                    mapWidth, mapHeight, initialStorageCapacity, storageCapacityUpgrade);

                            for (AID unitAID : (List<AID>) ListUtil.castJadeListToJavaList(initalizeTribe.getUnitList())) {
                                myUnits.put(unitAID,initalizeTribe.getStartingPosition());
                                // send welcome message to my newly created unit
                                WelcomeUnit welcomeUnit = new WelcomeUnit();
                                welcomeUnit.setTribeAID(getAID());
                                welcomeUnit.setStartingPosition(myUnits.get(unitAID));
                                ACLMessage welcomeUnitMessage = createInternalMessage(ACLMessage.REQUEST, unitAID);
                                welcomeUnitMessage.setProtocol(OwnOntology.WELCOME_UNIT);
                                sendActionTo(unitAID, welcomeUnitMessage, welcomeUnit);
                            }
                            isGameNow = true;

                            addBehaviour(new StrategyActionBehavior(myAgent,true,false,false, false,
                                    false,false,false,false, initalizeTribe.getStartingPosition(),
                                    ACLMessage.INFORM, null, concept));
                        } else if (concept instanceof MoveToCell) {
                            MoveToCell moveToCell = (MoveToCell) concept;
                            if (!CellUtil.hasSameCoordinates(moveToCell.getNewlyArrivedCell(),myUnits.get(message.getSender()))) {
                                log("%s moved to cell [%d,%d] with content %s", message.getSender().getLocalName(),
                                        moveToCell.getNewlyArrivedCell().getX(), moveToCell.getNewlyArrivedCell().getY(),
                                        moveToCell.getNewlyArrivedCell().getContent().getClass().getSimpleName());
                                myUnits.put(message.getSender(), moveToCell.getNewlyArrivedCell());
                            } else {
                                logDebug("Whoops");
                            }

                            if (builderUnits.containsKey(message.getSender())) {
                                builderUnits.put(message.getSender(), moveToCell.getNewlyArrivedCell());
                            }

                            addBehaviour(new StrategyActionBehavior(myAgent,false,false,false, false,
                                    false,true,false,false, moveToCell.getNewlyArrivedCell(),
                                    ACLMessage.INFORM, message.getSender(), concept));
                        } else if (concept instanceof ExploitResource) {
                            ExploitResource exploitResource = (ExploitResource) concept;
                            List<GainedResource> gainedResources = (List<GainedResource>) ListUtil.castJadeListToJavaList(exploitResource.getResourceList());
                            switch (gainedResources.size()) {
                                case 0:
                                    log("%s exploited some resource but my storage is full --> got nothing",
                                            message.getSender().getLocalName());
                                    needToIncreaseCapacity = true;
                                    break;
                                case 1:
                                    if (gainedResources.get(0).getResourceName().equalsIgnoreCase(RESOURCE_WOOD)) {
                                        resourceAccount.setWood(resourceAccount.getWood() + gainedResources.get(0).getAmount());
                                        log("%s exploited %d %s; now I have %d", message.getSender().getLocalName(),
                                                gainedResources.get(0).getAmount(), gainedResources.get(0).getResourceName(),
                                                resourceAccount.getWood());
                                    } else if (gainedResources.get(0).getResourceName().equalsIgnoreCase(RESOURCE_FOOD)) {
                                        resourceAccount.setFood(resourceAccount.getFood() + gainedResources.get(0).getAmount());
                                        log("%s harvested %d %s; now I have %d", message.getSender().getLocalName(),
                                                gainedResources.get(0).getAmount(), gainedResources.get(0).getResourceName(),
                                                resourceAccount.getFood());
                                    }
                                    break;
                                case 2:
                                    for (GainedResource gainedResource : gainedResources) {
                                        if (gainedResource.getResourceName().equalsIgnoreCase(RESOURCE_GOLD))
                                            resourceAccount.setGold(resourceAccount.getGold() + gainedResource.getAmount());
                                        else
                                            resourceAccount.setStone(resourceAccount.getStone() + gainedResource.getAmount());
                                    }
                                    log("%s exploited %d %s and %d %s; now I have %d gold & %d stone", message.getSender().getLocalName(),
                                            gainedResources.get(0).getAmount(), gainedResources.get(0).getResourceName(),
                                            gainedResources.get(1).getAmount(), gainedResources.get(1).getResourceName(),
                                            resourceAccount.getGold(), resourceAccount.getStone());
                                    break;
                            }
                            Cell currentCell = myUnits.get(message.getSender());
                            boolean isDepleted = false;
                            // probably that's not gonna work - add another check
                            if (currentCell.getContent() instanceof Ground) {
                                log("Cell [%d,%d] depleted to Ground",
                                        currentCell.getX(), currentCell.getY());
                                isDepleted = true;
                            }
                            addBehaviour(new StrategyActionBehavior(myAgent,false,false,false, false,
                                    false,false,isDepleted,true, currentCell,
                                    ACLMessage.INFORM, message.getSender(), concept));
                        } else if (concept instanceof RegisterTribe) {
                            log("Whohooooa, registration period is over! Let's play :)");
                            break;
                        } else if (concept instanceof EndOfGame) {
                            log("Game is over, muy mal :(");
                            isGameNow = false;
                            ACLMessage endOfGameMessage = createMessage(ACLMessage.INFORM, null);
                            for (AID unit : myUnits.keySet()) {
                                endOfGameMessage.addReceiver(unit);
                            }
                            sendActionTo(this.myAgent.getAID(),endOfGameMessage,(EndOfGame) concept);
                            break;
                        }
                        break;
                    }
                    case (ACLMessage.REFUSE): case (ACLMessage.FAILURE): case (ACLMessage.NOT_UNDERSTOOD): case (ACLMessage.AGREE): {
                        log("Get %s from %s to %s request", MsgUtil.getStringPerformative(message),
                                message.getSender().getLocalName(), extractConceptFromMessage(message).getClass().getSimpleName());
                        if (extractConceptFromMessage(message) instanceof RegisterTribe && message.getPerformative() == ACLMessage.NOT_UNDERSTOOD) {
                           addBehaviour(new SendRegistrationRequest(myAgent,new Random().nextInt(6)));
                        }
                        addBehaviour(new StrategyActionBehavior(myAgent,false,false,false, false,
                                false,false,false,false,null, message.getPerformative(), message.getSender(),
                                (AgentAction) extractConceptFromMessage(message)));
                        break;
                    }
                }
            } catch (Codec.CodecException | OntologyException e) {
                e.printStackTrace();

            }
        }
    }

    private void updateCellContent(Cell cell, CellContent cellContent) {
        for (Cell cell1 : exploredCells) {
            if (CellUtil.hasSameCoordinates(cell1, cell)) {
                cell1.setContent(cellContent);
                break;
            }
        }
    }

    private CellContent getCellContent(Cell cell) {
        for (Cell cell1 : exploredCells) {
            if (CellUtil.hasSameCoordinates(cell1,cell))
                return cell1.getContent();
        }
        return null;
    }

    private Integer calculateResources() {
        return resourceAccount.getFood() + resourceAccount.getGold()
                + resourceAccount.getStone() + resourceAccount.getWood();
    }

    private void updateResouceAccountCreation(int goldDiff, int foodDiff, int woodDiff, int stoneDiff) {
        resourceAccount.setGold(resourceAccount.getGold() - goldDiff);
        resourceAccount.setFood(resourceAccount.getFood() - foodDiff);
        resourceAccount.setWood(resourceAccount.getWood() - woodDiff);
        resourceAccount.setStone(resourceAccount.getStone() - stoneDiff);

        needToIncreaseCapacity = currentStorageCapacity <= calculateResources();
    }

    private class SendRegistrationRequest extends OneShotBehaviour {

        int teamNumber = -1;

        SendRegistrationRequest(Agent agent) {
            super(agent);
        }

        SendRegistrationRequest(Agent agent, int newTeamNumber) {
            teamNumber = newTeamNumber;
        }

        @Override
        public void action() {
            try {
                AID regDeskAID = null;
                int count = 0;

                while (count < 3) {
                    regDeskAID = findAgentByServiceType(myAgent, SERVICE_TYPE_REGISTRATION_DESK);
                    if (regDeskAID == null) {
                        log("Retrying to find RegDesk...");
                        count++;
                        synchronized (myAgent) {
                            AgTribe.this.wait(2 * TICK_MILLIS);
                        }
                    } else {
                        break;
                    }
                }

                if (regDeskAID == null) {
                    log("Couldn't find Reg Desk. Don't want to play :(");
                    return;
                }

                ACLMessage registrationTribeMessage = createMessage(ACLMessage.REQUEST, regDeskAID);
                registrationTribeMessage.setProtocol(GameOntology.REGISTERTRIBE);
                RegisterTribe registerTribe = new RegisterTribe();
                registerTribe.setTeamNumber(TEAM_NUMBER);

                // setting team number depending on reply we get from Reg Desk.
                // When we get NOT UNDERSTOOD - let's try another tribe number.
                //
                // IMPORTANT: just for TESTING!!
                //
                if (teamNumber != -1)
                registerTribe.setTeamNumber(teamNumber);

                log("Found Registration desk");
                sendActionTo(regDeskAID, registrationTribeMessage, registerTribe);
            } catch (Codec.CodecException | OntologyException e ) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class StrategyActionBehavior extends OneShotBehaviour {

        private boolean isNewCellExplored, isNewUnitCreated, isNewBuildingCreated, isCreatorMe;
        private boolean isUnitMoved;
        private boolean isCellDepleted;
        private boolean isResourceExploited;
        private boolean isTribeInitialized;
        private Cell cell;
        private AID senderAID;
        private Concept agentAction;

        private int performative; // performative of messages to differentiate them

        public StrategyActionBehavior(Agent agent, boolean isTribeInitialized,
                                      boolean isNewCellExplored, boolean isNewBuildingCreated, boolean isCreatorMe, boolean isNewUnitCreated,
                                      boolean isUnitMoved, boolean isCellDepleted, boolean isResourceExploited, Cell cell, int performative,
                                      AID aid, Concept agentAction) {
            super(agent);
            this.cell = cell;
            this.isCellDepleted = isCellDepleted;
            this.isNewBuildingCreated = isNewBuildingCreated;
            this.isCreatorMe = isCreatorMe;
            this.isNewCellExplored = isNewCellExplored;
            this.isNewUnitCreated = isNewUnitCreated;
            this.isResourceExploited = isResourceExploited;
            this.isTribeInitialized = isTribeInitialized;
            this.isUnitMoved = isUnitMoved;
            this.performative = performative;
            this.senderAID = aid;
            this.agentAction = agentAction;
        }

        @Override
    public void action() {
            // send anything only if game continues
            if (isGameNow) {

                // send some commands to units when tribe is just initialized
                if (isTribeInitialized) {
                    // add our units to workers list
                    //workerUnits.add(myUnits.keySet().iterator().next());
                    for (AID unitAID : myUnits.keySet()) {
                        //Add all starting units to workers
                        workerUnits.add(unitAID);

                        //
                        // USE THAT block for testing some features in further development; for playing a game use runStrategy
                        //
//                            MoveToCell moveToCell = new MoveToCell();
//                            moveToCell.setTargetDirection(new Random().nextInt(6)+1);
//                            ACLMessage message = createMessage(ACLMessage.REQUEST, unitAID);
//                            //sendActionTo(unitAID,message,moveToCell);
//                            CreateBuilding createBuilding = new CreateBuilding();
//                            createBuilding.setBuildingType(CONCEPT_TOWN_HALL);
//                            //sendActionTo(unitAID,message,createBuilding);
//                            ExploitResource exploitResource = new ExploitResource();
//                            sendActionTo(unitAID,message,exploitResource);
                    }

                    runStrategy(true,false, null);
                    return;
                }

                // send some commands to units when new cell explored - USE TYPE of Cell Content here : Ground, Resource, Building
                if (isNewCellExplored) {
                    // nothing to add now --> could be improved in further development
                }

                // send some commands to units when new unit created - updating our list of workers
                // we are using senderAID as new unit AID
                if (isNewUnitCreated) {
                    updateWorkerlist(senderAID);
                }


                // send some commands to units when new building created - use TYPE of building here
                if (isNewBuildingCreated) {
                    if (isCreatorMe) {
                        Building content = new Building();
                        content.setOwner(this.getAgent().getAID());
                        cell.setContent(getCellContent(cell));
                        content.setType(((Building) cell.getContent()).getType());
                        markUnitFree(content,cell,null);
                    } else {
                        // sth else
                    }
                }

                // send some commands to units when tribe is just initialized - USE TYPE of Cell Content here also
                if (isUnitMoved || isResourceExploited ) {
                    markUnitFree(agentAction,cell,senderAID);
                }

                //
                // might be useless???
                //
//                // send some commands to units when cell depleted to Ground
//                if (isCellDepleted) {
//                    markUnitFree(cell);
//                }



                // try to adapt strategy when receive REFUSE response based on unit AID and it's position
                // maybe he was trying to create new unit, building or to move or to exploit and got the refuse
                boolean refuseForBuilding = false;
                AID refusedUnit = null;
                if (performative == ACLMessage.REFUSE) {
                    markUnitWaiting(senderAID,false);
                    // actions based on concept (agentAction)
                    if (agentAction instanceof CreateUnit) {
                        // check if we don't have enough resources of if we are not in Town Hall or unit is busy and do strategy
                    } else if (agentAction instanceof CreateBuilding) {
                        // we don't have enough resources or cell is busy or unit is busy
                        refuseForBuilding = true;
                        refusedUnit = senderAID;
                    } else if (agentAction instanceof MoveToCell) {
                        // unit is busy
                    } else if (agentAction instanceof ExploitResource) {
                        // unit is not in Resource cell or Farm is not ours or unit us busy

                        // cell was depleted by somebody else before we know that by trying to exploit
                        cell = myUnits.get(senderAID);
                        if (cell.getContent() instanceof Resource) {
                            log("Cell [%d,%d] was earlier depleted by my other unit or another tribe's one. I'm updating my knowledge",
                                    cell.getX(), cell.getY());
                            cell.setContent(new Ground());
                            //updateCellContent(cell, new Ground());
                        }
                    }
                }

                // calculate our resources when we receive AGREE to CreateUnit or CreateBuilding
                if (performative == ACLMessage.AGREE) {
                    if (agentAction instanceof MoveToCell) {
                        markUnitBusy(senderAID,agentAction);
                    }
                    else {
                        if (agentAction instanceof CreateUnit) {
                            updateResouceAccountCreation(PRICE_UNIT_GOLD, PRICE_UNIT_FOOD, 0, 0);
                        } else if (agentAction instanceof CreateBuilding) {
                            switch (((CreateBuilding) agentAction).getBuildingType()) {
                                case CONCEPT_TOWN_HALL:
                                    //Make the unit that built the TownHall a builder
                                    townHallsBuilt += 1;
                                    makeWorkerBuilder(senderAID);
                                    updateResouceAccountCreation(PRICE_TOWN_HALL_GOLD, 0, PRICE_TOWN_HALL_WOOD, PRICE_TOWN_HALL_STONE);
                                    break;
                                case CONCEPT_FARM:
                                    farms += 1;
                                    updateResouceAccountCreation(PRICE_FARM_GOLD, 0, PRICE_FARM_WOOD, PRICE_FARM_STONE);
                                    break;
                                case CONCEPT_STORE:
                                    stores += 1;
                                    updateResouceAccountCreation(PRICE_STORE_GOLD, 0, PRICE_STORE_WOOD, PRICE_STORE_STONE);
                                    break;
                            }
                            Building content = new Building();
                            content.setOwner(this.getAgent().getAID());
                            content.setType(((CreateBuilding) agentAction).getBuildingType());
                            markUnitBusy(senderAID, content);
                        } else if (agentAction instanceof ExploitResource) {
                            cell = myUnits.get(senderAID);
                            if (cell.getContent() instanceof Resource) {
                                Resource resource = (Resource) cell.getContent();
                                resource.setResourceAmount(resource.getResourceAmount() - RESOURCE_AMOUNT_BLOCKAGE);
                                if (resource.getResourceAmount() <= 0) {
                                    log("Cell [%d,%d] will be depleted after this mining by my unit", cell.getX(), cell.getY());
                                    cell.setContent(new Ground());
                                }
                            }
                            markUnitBusy(senderAID, agentAction);
                        } else {
                            return;
                        }
                        log("My current resource account: [Gold - %d], [Food - %d], [Wood - %d], [Stone - %d]",
                                resourceAccount.getGold(), resourceAccount.getFood(), resourceAccount.getWood(), resourceAccount.getStone());
                    }
                    logDebug("%s is now busy - %s", senderAID.getLocalName(), !isUnitFree(senderAID));
                }

                // run strategy after all updates if we get REFUSE or INFORM
                if (performative == ACLMessage.INFORM || performative == ACLMessage.REFUSE)
                    runStrategy(false, refuseForBuilding, refusedUnit);
            }
        }


        private void runStrategy(boolean firstTime, boolean refuseForBuilding, AID refusedUnitAID){
            int unitNumber = myUnits.size();

            // if we launch strategy for the first time
            if (firstTime) {
                Iterator<AID> iterator = workerUnits.iterator();
                if (canAfford(PRICE_TOWN_HALL_GOLD, PRICE_TOWN_HALL_WOOD, PRICE_TOWN_HALL_STONE, 0)) {
                    buildNewTownhall(iterator.next());
                }
                while (iterator.hasNext()){
                    harvestOrExplore(iterator.next());
                }
                return;
            }

            ///////////////////////////////////
            //WORKERS
            for (AID unit : workerUnits) {
                if (isUnitFree(unit)) {
                    if (canAfford(PRICE_TOWN_HALL_GOLD, PRICE_TOWN_HALL_WOOD, PRICE_TOWN_HALL_STONE, 0) &&
                        canCreateTownHallAdjacentCells(myUnits.get(unit)) &&
                            (refusedUnitAID == null || !AIDUtil.equals(refusedUnitAID,unit))) {
                        if (townHallsBuilt == 0 || unitNumber / townHallsBuilt > UNITS_PER_CITY) {
                            buildNewTownhall(unit);
                        } else {
                            harvestOrExplore(unit);
                        }
                    } else {
                        harvestOrExplore(unit);
                    }
                }
            }

            ///////////////////////////////////
            //BUILDERS
            //If you can afford a unit, make a unit
            AID builder;
            for(AID unit : builderUnits.keySet()){
                if(isUnitFree(unit)){
                    builder = unit;

                    // first check if we are in need to create store - so the order is different
                    if (needToIncreaseCapacity) {
                        if(canAfford(PRICE_STORE_GOLD, PRICE_STORE_WOOD, PRICE_STORE_STONE, 0)) {
                            buildFarmOrStore(builder, CONCEPT_STORE);
                            continue;
                        }
                    }

                    if(canAfford(PRICE_UNIT_GOLD, 0, 0, PRICE_UNIT_FOOD)){
                        buildUnit(builder);
                        continue;
                    }

                    if(canAfford(PRICE_STORE_GOLD, PRICE_STORE_WOOD, PRICE_STORE_STONE, 0)){
                        if (stores == 0 || unitNumber / stores > UNITS_PER_STORE) {
                            buildFarmOrStore(builder, CONCEPT_STORE);
                            continue;
                        }
                    }

                    if(canAfford(PRICE_FARM_GOLD, PRICE_FARM_WOOD, PRICE_FARM_STONE, 0)){
                        if (farms == 0 || unitNumber / farms > UNITS_PER_FARM) {
                            buildFarmOrStore(builder, CONCEPT_FARM);
                            continue;
                        }
                    }

                    else{
                        // here we might not have farms yet - so will just move around
                        farmClosestFarm(builder);
                    }
                    break;
                }
            }
        }

        // return if there is no any building in any adjacent cells among known
        private boolean canCreateTownHallAdjacentCells(Cell cell) {
            List<Cell> adjacentKnownCells = getExploredAdjacentCells(cell);
            for (Cell cell1 : adjacentKnownCells) {
                if (cell1.getContent() instanceof Building)
                    return false;
            }
            return true;
        }

        //Makes a builder unit farm the first farm in his city
        private void farmClosestFarm(AID unit){
            // fist check if we are already in Farm or sth else
            CellContent cellContent = myUnits.get(unit).getContent();

            if (cellContent instanceof Building) {
                // if we are in farm - do harvesting
                if (((Building) cellContent).getType().equalsIgnoreCase(CONCEPT_FARM) &&
                        AIDUtil.equals(((Building) cellContent).getOwner(), this.getAgent().getAID())) {
                    // if
                    if (isInNeedToHarvest()) {
                        sendHarvestRequest(unit);
                        return;
                    }
                } else {
                    // if we are in TownHall - check for farm in adjacent cells
                    if (((Building) cellContent).getType().equalsIgnoreCase(CONCEPT_TOWN_HALL)) {
                        ArrayList<Cell> cityCells = getExploredAdjacentCells(myUnits.get(unit));

                        // we send move request to a farm if found in explored cells
                        for(Cell citycell : cityCells){
                            if(citycell.getContent() instanceof Building){
                                Building building = (Building) citycell.getContent();
                                if(building.getType().equalsIgnoreCase(CONCEPT_FARM) &&
                                    AIDUtil.equals(building.getOwner(),this.getAgent().getAID())){
                                    sendMoveRequest(unit,   findDirection(myUnits.get(unit), citycell));
                                    return;
                                }
                            }
                        }

                        // if we don't find any farm in adjacent cells (or size of list is zero) - then move to random cell
                        moveToUnexploredOrRandom(unit);
                        return;
                    } else {
                        // if we are in Store - nothing to do, we are out Town Hall - let's move somewhere
                    }
                }
            }

            // if unit is standing out Town Hall and not in Farm (so, in Ground\Resource\Store cell
            // first find the cell with Town Hall
            Cell townHallCell = findTownHallForBuilder(unit);
            int directionToTownHall = 0;

            if (townHallCell != null) {
                // we need to calculate whether there is any farm in two adjacent cityCells
                directionToTownHall = findDirection(myUnits.get(unit), townHallCell);
                int directionRight = (directionToTownHall == 1) ? 6 : directionToTownHall - 1;
                int directionLeft = (directionToTownHall == 6) ? 1 : directionToTownHall + 1;
                Cell adjacentCellLeft = CellUtil.calculateTargetCell(myUnits.get(unit), directionLeft, mapHeight, mapWidth);
                Cell adjacentCellRight = CellUtil.calculateTargetCell(myUnits.get(unit), directionRight, mapHeight, mapWidth);
                if (findCellAmongExplored(adjacentCellLeft) && adjacentCellLeft.getContent() instanceof Building &&
                        ((Building) adjacentCellLeft.getContent()).getType().equalsIgnoreCase(CONCEPT_FARM)) {
                    sendMoveRequest(unit, directionLeft);
                    return;
                } else if (findCellAmongExplored(adjacentCellRight) && adjacentCellRight.getContent() instanceof Building &&
                        ((Building) adjacentCellRight.getContent()).getType().equalsIgnoreCase(CONCEPT_FARM)) {
                    sendMoveRequest(unit, directionRight);
                    return;
                } else if (!findCellAmongExplored(adjacentCellLeft)) {
                    sendMoveRequest(unit,directionLeft);
                    return;
                } else if (!findCellAmongExplored(adjacentCellRight)) {
                    sendMoveRequest(unit,directionRight);
                    return;
                }

                // if nothing exists in adjacent cells while standing out the city - move back to Town Hall
                sendMoveRequest(unit,directionToTownHall);
                return;
            }

            // if everything is super bad - move randomly LOL (almost impossible)
            moveToUnexploredOrRandom(unit);

        }

        private Cell findTownHallForBuilder(AID unit) {
            ArrayList<Cell> adjacentCells = getExploredAdjacentCells(myUnits.get(unit));
            for(Cell cell : adjacentCells){
                if(cell.getContent() instanceof Building){
                    Building building = (Building) cell.getContent();
                    if(building.getType().equalsIgnoreCase(CONCEPT_TOWN_HALL) &&
                            AIDUtil.equals(building.getOwner(),this.getAgent().getAID())){
                        return cell;
                    }
                }
            }
            return null;
        }

        //Make the first unit that is not busy and is standing on plain ground build a townhall
        //If we can afford it
        private boolean buildNewTownhall(AID unitAID){
            if (isUnitFree(unitAID)) {
                if (myUnits.get(unitAID).getContent() instanceof Ground) {
                    sendBuildBuildingRequest(unitAID,CONCEPT_TOWN_HALL);
                } else {
                    moveToUnexploredOrRandom(unitAID);
                }
            }
            return false;
        }

        //Moves to clear adjacent ground if standing on townhall
        //Builds farm\store if standing on clear ground
        //Moves towards other cell if ground is not clear
        private void buildFarmOrStore(AID unit, String type){
            //Get adjacent cells of townHall
            ArrayList<Cell> adjacentCells = getExploredAdjacentCells(myUnits.get(unit));

            // if there is nothing around already explored - explore myself
            if (adjacentCells.size() == 0) {
                moveToUnexploredOrRandom(unit);
                return;
            }

            //If unit is standing on top of townhall
            CellContent cellContent = myUnits.get(unit).getContent();
            if (cellContent instanceof Building && ((Building) cellContent).getType().equalsIgnoreCase(CONCEPT_TOWN_HALL)) {
                for(Cell adjacentCell : adjacentCells){
                    if(adjacentCell.getContent() instanceof Ground){
                        int direction = findDirection(myUnits.get(unit), adjacentCell);
                        sendMoveRequest(unit, direction);
                        return;
                    }
                }
            } else if (cellContent instanceof Ground) {
                // if unit stands in Ground cell with adjacent Town Hall (or, actually, another building) of his tribe
                for (Cell cell : myBuildings) {
                    if (areAdjacent(cell,myUnits.get(unit))) {
                        sendBuildBuildingRequest(unit,type);
                        return;
                    }
                }
            } else {
                // if unit stand in Building cell (not Town Hall) or in Resource cell

            }

            Cell townHallCell = findTownHallForBuilder(unit);

            if (townHallCell != null) {
                // we need to calculate whether there is any farm in two adjacent cityCells
                int directionToTownHall = findDirection(myUnits.get(unit), townHallCell);
                int directionRight = (directionToTownHall == 1) ? 6 : directionToTownHall - 1;
                int directionLeft = (directionToTownHall == 6) ? 1 : directionToTownHall + 1;
                Cell adjacentCellLeft = CellUtil.calculateTargetCell(myUnits.get(unit), directionLeft, mapHeight, mapWidth);
                Cell adjacentCellRight = CellUtil.calculateTargetCell(myUnits.get(unit), directionRight, mapHeight, mapWidth);
                if (findCellAmongExplored(adjacentCellLeft) && adjacentCellLeft.getContent() instanceof Ground) {
                    sendMoveRequest(unit, directionLeft);
                    return;
                } else if (findCellAmongExplored(adjacentCellRight) && adjacentCellRight.getContent() instanceof Ground) {
                    sendMoveRequest(unit, directionRight);
                    return;
                } else if (!findCellAmongExplored(adjacentCellLeft)) {
                    sendMoveRequest(unit,directionLeft);
                    return;
                } else if (!findCellAmongExplored(adjacentCellRight)) {
                    sendMoveRequest(unit,directionRight);
                    return;
                }

                // if nothing exists in adjacent cells while standing out the city - move back to Town Hall
                sendMoveRequest(unit, directionToTownHall);
                return;
            }

            // finally - move randomly if not found town hall nearby
            moveToUnexploredOrRandom(unit);

        }

        private void buildUnit(AID unit){
            //If unit is standing on top of townhall
            if(CellUtil.hasSameCoordinates(builderUnits.get(unit), myUnits.get(unit))){
                sendBuildUnitRequest(unit);
                //else move towards townhall
            }else{
                int direction = findDirection(myUnits.get(unit), builderUnits.get(unit));
                sendMoveRequest(unit, direction);
                return;
            }
        }

        //All workers harvest if they are on top of resources and move if not
        private void harvestOrExplore(AID unitAID){
            if(isUnitFree(unitAID)) {
                if (myUnits.get(unitAID).getContent() instanceof Resource &&
                        isInNeedToExploit(unitAID)) {
                    sendHarvestRequest(unitAID);
                } else {
                    moveToUnexploredOrRandom(unitAID);
                }
            }
        }

        boolean isInNeedToExploit(AID unitAID) {
            Cell cell = myUnits.get(unitAID);
            Resource cellContent = (Resource) cell.getContent();
            if (cellContent.getResourceType().equalsIgnoreCase(CONCEPT_FOREST) &&
//                    (resourceAccount.getWood() < PRICE_TOWN_HALL_WOOD &&
//                            isInCapacitySize(PRICE_TOWN_HALL_WOOD,resourceAccount.getWood()) ||
//                    (resourceAccount.getWood() < PRICE_STORE_WOOD &&
//                            isInCapacitySize(PRICE_STORE_WOOD,resourceAccount.getWood())) ||
//                    (resourceAccount.getWood() < PRICE_FARM_WOOD &&
//                            isInCapacitySize(PRICE_FARM_WOOD,resourceAccount.getWood())))) {
                    resourceAccount.getWood() < PRICE_TOWN_HALL_WOOD && !needToIncreaseCapacity) {
                return true;
            } else if (cellContent.getResourceType().equalsIgnoreCase(CONCEPT_MINE) &&
//                    (resourceAccount.getGold() < PRICE_TOWN_HALL_GOLD &&
//                            isInCapacitySize(PRICE_TOWN_HALL_GOLD,resourceAccount.getGold()) ||
//                    resourceAccount.getGold() < PRICE_FARM_GOLD &&
//                            isInCapacitySize(PRICE_FARM_GOLD,resourceAccount.getGold()) ||
//                    resourceAccount.getGold() < PRICE_STORE_GOLD &&
//                            isInCapacitySize(PRICE_STORE_GOLD,resourceAccount.getGold())) &&
//                    (resourceAccount.getStone() < PRICE_TOWN_HALL_STONE &&
//                            isInCapacitySize(PRICE_TOWN_HALL_STONE,resourceAccount.getGold()) ||
//                            resourceAccount.getGold() < PRICE_STORE_STONE &&
//                                    isInCapacitySize(PRICE_STORE_STONE,resourceAccount.getGold()) ||
//                            resourceAccount.getGold() < PRICE_FARM_STONE &&
//                                    isInCapacitySize(PRICE_FARM_STONE,resourceAccount.getGold())) ) {
                    (resourceAccount.getGold() < PRICE_TOWN_HALL_GOLD ||
                            resourceAccount.getStone() < PRICE_TOWN_HALL_STONE) && !needToIncreaseCapacity) {
                return true;
            } else {
                return false;
            }
        }

        boolean isInNeedToHarvest() {
            return (resourceAccount.getFood() < PRICE_UNIT_FOOD && !needToIncreaseCapacity);
                    //isInCapacitySize(PRICE_UNIT_FOOD,resourceAccount.getFood()));
        }

        boolean isInCapacitySize(int price, int currentResource) {
            return (price - currentResource) < (currentStorageCapacity - calculateResources());
        }

        //Move to an unexplored adjacent cell, or random if all are explored
        private void moveToUnexploredOrRandom(AID unit){
            if (isUnitFree(unit)) {
                ArrayList<Cell> adjKnownCells = getExploredAdjacentCells(myUnits.get(unit));
                //If all adjacent cells are (un) explored move a random direction
                if (adjKnownCells.size() == 6 || adjKnownCells.size() == 0) {
                    int random = new Random().nextInt(6) + 1;
                    sendMoveRequest(unit, random);
                } else {
                    List<Integer> adjDirections = new ArrayList<>();
                    for (Cell adj : adjKnownCells) {
                        adjDirections.add(findDirection(myUnits.get(unit), adj));
                    }
                    List<Integer> unexploredAdjacentDirections = new ArrayList<>();
                    //Check what direction is unexplored
                    for (int i = 1; i <= 6; i++) {
                        if (!adjDirections.contains(i)) {
                            // store in list of unexplored
                            unexploredAdjacentDirections.add(i);
                        }
                    }
                    // sent unit to one of cell unexplored with direction to it chosen randomly
                    sendMoveRequest(unit,
                            unexploredAdjacentDirections.get(new Random().nextInt(unexploredAdjacentDirections.size())));
                }
            }
        }

        private void makeWorkerBuilder(AID unit){
            if(workerUnits.contains(unit)) {
                workerUnits.remove(unit);
            }
            if(!builderUnits.containsKey(unit)) {
                builderUnits.put(unit, myUnits.get(unit));
            }
        }

        private void markUnitBusy(AID unit, Concept agentAction){
            synchronized (this.myAgent) {
                if (!busyUnits.containsKey(unit)) {
                    busyUnits.put(unit, agentAction);
                }
            }
        }
        //Match position sent by world from completed action
        //If busyunits contain a unit with that position, remove it
        private void markUnitFree(Concept agentAction, Cell cell, AID unit){
            // if unit did MoveToCell or ExploitResource - AID is not null because the message was received from unit
            if (unit != null) {
                busyUnits.remove(unit);
                markUnitWaiting(unit,false);
            } else {
                // if we receive NotifyCellDetail inform with Building we created so we don't know exactly the unit AID - need to find
                synchronized (this.myAgent) {
                    for (AID aid : busyUnits.keySet()) {
                        CellContent cellContent = myUnits.get(aid).getContent();
                        if (//compareBuilgindActions((Building) agentAction, (Building) cellContent) &&
                                CellUtil.hasSameCoordinates(cell, myUnits.get(aid))) {
                            busyUnits.remove(aid);
                            markUnitWaiting(aid, false);
                            return;
                        }
                    }
                }
            }
        }

        private boolean compareBuilgindActions(Building agentAction1, Building agentAction2) {
            return agentAction1.getType().equalsIgnoreCase(agentAction2.getType()) &&
                    AIDUtil.equals(agentAction1.getOwner(), agentAction2.getOwner());
        }

        private boolean isUnitFree(AID unit){
            //return !busyUnits.containsKey(unit);
            return !busyUnits.containsKey(unit) && !isUnitWaiting(unit);
        }

        private void markUnitWaiting(AID unit, boolean flag) {
            if (flag)
                unitsInWaiting.add(unit);
            else unitsInWaiting.remove(unit);
        }

        private boolean isUnitWaiting(AID unit) {
            return unitsInWaiting.contains(unit);
        }

        //Gives the direction a unit should move to reach a cell
        private int findDirection(Cell unitPos, Cell targetPos){
            int ux = unitPos.getX();
            int uy = unitPos.getY();
            int bx = targetPos.getX();
            int by = targetPos.getY();
            int xSum = ux - bx;
            int ySum = uy - by;

            // first check if xSum or ySum difference is bigger than 2 - so use another kind
            if (xSum > 2 || ySum > 2) {
                for (int i = 1; i < 7; i++) {
                    if (CellUtil.hasSameCoordinates(targetPos,
                            CellUtil.calculateTargetCell(unitPos,i,mapHeight,mapWidth))) {
                        return i;
                    }
                }
            }

            //If the direction of the target is upwards
            if(xSum > 0){
                if(ySum > 0)
                    //Move top left
                    return 6;
                if(ySum == 0)
                    //Move up
                    return 1;
                if (ySum < 0)
                    //Move top right
                    return 2;
            }
            //Else if it's downwards
            else if(xSum < 0){
                if(ySum > 0)
                    //Move bottom left
                    return 5;
                if(ySum == 0)
                    //Move down
                    return 4;
                if (ySum < 0)
                    //Move bottom right
                    return 3;
            }
            //It's on the same x-axis, but farther away
            else{
                if (ySum > 0)
                    //Move top left
                    return 6;
                if (ySum < 0)
                    //Move top right
                    return 2;
            }
            //The unit is in the goal position
            return 0;

        }

        //Returns all adjacent cells to a cell
        private ArrayList<Cell> getExploredAdjacentCells(Cell cell) {
            ArrayList<Cell> list = new ArrayList<>();
            Cell nCell = null;
            for (int i = 1; i < 7; i++) {
                nCell = CellUtil.calculateTargetCell(cell,i,mapHeight,mapWidth);
                if (findCellAmongExplored(nCell)) {
                    nCell.setContent(getCellContent(nCell));
                    list.add(nCell);
                }
            }

            return list;
        }

        private boolean areAdjacent(Cell c1, Cell c2){
            for (int i = 1; i < 7; i++) {
                if (CellUtil.hasSameCoordinates(c2,
                        CellUtil.calculateTargetCell(c1,i,mapHeight,mapWidth)))
                    return true;
            }
            return false;
        }

        private boolean findCellAmongExplored(Cell searchCell) {
            for (Cell cell : exploredCells) {
                if (CellUtil.hasSameCoordinates(cell, searchCell)) {
                    return true;
                }
            }
            return false;
        }

        private void updateWorkerlist(AID newUnitAID){
            workerUnits.add(newUnitAID);
        }

        private void sendBuildBuildingRequest(AID unit, String type) {
            markUnitWaiting(unit, true);
            CreateBuilding createBuilding = new CreateBuilding();
            createBuilding.setBuildingType(type);
            ACLMessage message = createMessage(ACLMessage.REQUEST, unit);
            try {
                sendActionTo(unit, message, createBuilding);
            } catch (Codec.CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }

        private void sendBuildUnitRequest(AID unit){
            markUnitWaiting(unit, true);
            CreateUnit createUnit = new CreateUnit();
            ACLMessage message = createMessage(ACLMessage.REQUEST, unit);
            try {
                sendActionTo(unit, message, createUnit);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
        }

        private void sendMoveRequest(AID unit, int direction){
            markUnitWaiting(unit, true);
            MoveToCell moveToCell = new MoveToCell();
            moveToCell.setTargetDirection(direction);
            ACLMessage message = createMessage(ACLMessage.REQUEST, unit);
            try {
                sendActionTo(unit,message,moveToCell);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
        }

        private void sendHarvestRequest(AID unit){
            markUnitWaiting(unit, true);
            ExploitResource exploitResource = new ExploitResource();
            ACLMessage message = createMessage(ACLMessage.REQUEST, unit);
            try {
                sendActionTo(unit, message, exploitResource);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
        }

        private boolean canAfford(int goldCost, int woodCost,
                              int stoneCost, int foodCost){
            int currentGold = resourceAccount.getGold();
            int currentWood = resourceAccount.getWood();
            int currentStone = resourceAccount.getStone();
            int currentFood = resourceAccount.getFood();

            if(currentGold >= goldCost && currentWood >= woodCost &&
            currentStone >= stoneCost && currentFood >= foodCost){
                return true;
            }
            else{
                return false;
            }
        }
    }
}