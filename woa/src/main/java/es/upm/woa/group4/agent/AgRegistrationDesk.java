package es.upm.woa.group4.agent;

import static es.upm.woa.group4.common.Constants.DURATION_REGISTRATION;
import static es.upm.woa.group4.common.Constants.SERVICE_TYPE_REGISTRATION_DESK;
import static es.upm.woa.group4.common.Constants.SERVICE_TYPE_WORLD;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import es.upm.woa.group4.common.Config;
import es.upm.woa.group4.protocol.InitiateTribes;
import es.upm.woa.group4.protocol.OwnOntology;
import es.upm.woa.group4.util.FileUtil;
import es.upm.woa.ontology.RegisterTribe;
import jade.content.Concept;
import jade.content.lang.Codec;
import jade.content.onto.OntologyException;
import jade.content.onto.UngroundedException;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;

public class AgRegistrationDesk extends AgBase {

    private static final String TAG = "Registration Desk";
    private HashMap<Integer, AID> tribesRegistered = new HashMap<>();
    private InitiateTribes initiateTribes = new InitiateTribes();
    private boolean isRegistrationPeriod = false;

    @Override
    protected String getServiceType() {
        return SERVICE_TYPE_REGISTRATION_DESK;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void setup() {
        super.setup();
        isRegistrationPeriod = true;
        addBehaviour(new ReceiveMessageBehavior());
        addBehaviour(new EndRegistrationPeriodBehavior(this, DURATION_REGISTRATION));
    }

    private class ReceiveMessageBehavior extends CyclicBehaviour {

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

                    if (concept instanceof RegisterTribe) {
                        RegisterTribe registerTribe = (RegisterTribe) concept;
                        Integer teamNumber = registerTribe.getTeamNumber();

                        log("Got registration request from team %d", teamNumber);

                        // if the registration period is over - then send RefuseReply
                        // if (!isRegistrationPeriod) {
                        // sendRefuseReply(message, "Registration period is over");
                        // return;
                        // }
                        //
                        // // if team number is out of range [1;6] - then send RefuseReply
                        // if (teamNumber < 1 || teamNumber > 6) {
                        // sendRefuseReply(message, "Incorrect team number");
                        // return;
                        // }

                        // if registration period is over or if team number not in range [1;6] - send
                        // Refuse
                        if (!isRegistrationPeriod || (teamNumber < 1 || teamNumber > 6)) {
                            sendRefuseReply(message, registerTribe);
                            return;
                        }

                        // if the tribe was already registered - then it will receive NotUnderstoodReply
                        if (tribesRegistered.containsKey(teamNumber)) {
                            // sendNotUnderstoodReply(message);
                            sendNotUnderstoodReply(message, registerTribe);
                            return;
                        }

                        // else register the team and send agree
                        tribesRegistered.put(teamNumber, message.getSender());
                        initiateTribes.addTribeList(message.getSender());
                        // sendAgreeReply(message);
                        sendAgreeReply(message, registerTribe);

                        // INFORM will be sent after the registration period is over
                    }
                }
                }

            } catch (UngroundedException e) {
                e.printStackTrace();
            } catch (Codec.CodecException | OntologyException e) {
                e.printStackTrace();
            }
        }
    }

    private class EndRegistrationPeriodBehavior extends WakerBehaviour {

        public EndRegistrationPeriodBehavior(Agent agent, long timeout) {
            super(agent, timeout);
        }

        @Override
        protected void handleElapsedTimeout() {

            try {
                isRegistrationPeriod = false;
                // send info to AgWorld with a list of tribes registered
                addBehaviour(new SendTribesForInitializing(myAgent));

                List<String> tribeNames = new ArrayList<>();
                // send INFORM to every tribe who was registered on time - USELESS
                for (AID teamAID : tribesRegistered.values()) {
                    // sendInformTo(teamAID, MESSAGE_GAME_STARTS);
                    sendInformTo(teamAID, new RegisterTribe());
                    tribeNames.add(teamAID.getLocalName());
                }

                callStartApi(tribeNames);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
        }

        private void callStartApi(List<String> tribeNames) {
            String mapPath = Config.getInstance().getMapPath();
            InputStream stream = getClass().getClassLoader().getResourceAsStream(mapPath);
            String mapJson = FileUtil.getContentOfResource(stream);
            Map<String, Object> body = new HashMap<>();
            body.put("map", new Gson().fromJson(mapJson, JsonElement.class));
            body.put("players", tribeNames);
            callApi("/start", body);
        }
    }

    private class SendTribesForInitializing extends OneShotBehaviour {

        SendTribesForInitializing(Agent agent) {
            super(agent);
        }

        @Override
        public void action() {
            try {
                AID worldAID = findAgentByServiceType(myAgent, SERVICE_TYPE_WORLD);
                ACLMessage initiateTribesMessage = createInternalMessage(ACLMessage.REQUEST, worldAID);
                initiateTribesMessage.setProtocol(OwnOntology.INITIATETRIBES);
                sendActionTo(worldAID, initiateTribesMessage, initiateTribes);
            } catch (Codec.CodecException e) {
                e.printStackTrace();
            } catch (OntologyException e) {
                e.printStackTrace();
            }
        }
    }
}