package es.upm.woa.group4.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;

import es.upm.woa.group4.common.Config;
import es.upm.woa.group4.protocol.OwnOntology;
import es.upm.woa.group4.util.DirectoryRegistrar;
import es.upm.woa.group4.util.Log;
import es.upm.woa.ontology.GameOntology;
import jade.content.AgentAction;
import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.UngroundedException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public abstract class AgBase extends Agent {

    private Codec codec = new SLCodec();
    private Ontology ontology = GameOntology.getInstance();
    private Ontology ownOntology = OwnOntology.getInstance();

    protected Codec getCodec() {
        return codec;
    }

    protected String getCodecName() {
        return getCodec().getName();
    }

    protected Ontology getOntology() {
        return ontology;
    }

    protected String getOntologyName() {
        return getOntology().getName();
    }

    protected abstract String getServiceType();

    protected abstract String getTag();

    @Override
    protected void setup() {
        logDebug("Setting up");
        getContentManager().registerLanguage(codec);
        getContentManager().registerOntology(ontology);
        getContentManager().registerOntology(ownOntology);
        registerToDF();
        logDebug("Setup completed");
    }

    protected void callApi(String path, Map<String, Object> body) {
        try {
            String jsonBody = new Gson().toJson(body);
            logDebug("Calling api '%s' with body '%s'", path, jsonBody);
            HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            // HttpClient httpClient = HttpClient.newBuilder()
            // .proxy(ProxySelector.of(new InetSocketAddress("localhost", 8080))).build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(Config.getInstance().getEndpoint() + path))
                    .header("Content-Type", "application/json").POST(BodyPublishers.ofString(jsonBody)).build();
            httpClient.send(httpRequest, BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected ACLMessage createInternalMessage(int performative, AID receiverAID) {
        ACLMessage message = createMessage(performative, receiverAID);
        message.setOntology(ownOntology.getName());
        return message;
    }

    protected ACLMessage createMessage(int performative, AID receiverAID) {
        ACLMessage message = new ACLMessage(performative);
        message.addReceiver(receiverAID);
        message.setConversationId(UUID.randomUUID().toString());
        message.setLanguage(getCodecName());
        message.setOntology(getOntologyName());
        return message;
    }

    protected ACLMessage createReply(ACLMessage message, int performative) {
        ACLMessage replyMessage = message.createReply();
        replyMessage.setPerformative(performative);
        return replyMessage;
    }

    protected Concept extractConceptFromMessage(ACLMessage message)
            throws UngroundedException, CodecException, OntologyException {
        ContentElement content = getContentManager().extractContent(message);
        if (content instanceof Action) {
            Action action = (Action) content;
            return action.getAction();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected <T> T extractRequestFromMessage(ACLMessage message)
            throws UngroundedException, CodecException, OntologyException {
        return (T) extractConceptFromMessage(message);
    }

    protected AID findAgentByServiceType(Agent agent, String type) {
        List<AID> agents = findAgentsByServiceType(agent, type);
        if (agents.size() > 0) {
            return agents.get(0);
        }
        return null;
    }

    protected List<AID> findAgentsByServiceType(Agent agent, String type) {
        try {
            ServiceDescription serviceDescription = new ServiceDescription();
            serviceDescription.setType(type);
            DFAgentDescription agentDescription = new DFAgentDescription();
            agentDescription.addServices(serviceDescription);
            DFAgentDescription[] agents = DFService.search(agent, agentDescription);
            List<AID> agentAIDs = new ArrayList<>();
            if (agents.length > 0) {
                for (DFAgentDescription ag : agents) {
                    agentAIDs.add(ag.getName());
                }
            }
            return agentAIDs;
        } catch (FIPAException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected void log(String message) {
        Log.info(this.getLocalName(), message);
    }

    protected void log(String format, Object... args) {
        // Log.info(this.getTag(), format, args);
        Log.info(this.getLocalName(), format, args);
    }

    protected void logDebug(String message) {
        Log.debug(this.getLocalName(), message);
    }

    protected void logDebug(String format, Object... args) {
        Log.debug(this.getLocalName(), format, args);
    }

    private void registerToDF() {
        DirectoryRegistrar.register(this, this.getServiceType());
        log("Registered to DF");
    }

    protected void sendActionTo(AID receiverAID, ACLMessage message, AgentAction agentAction)
            throws CodecException, OntologyException {
        Action action = new Action(receiverAID, agentAction);
        getContentManager().fillContent(message, action);
        send(message);
    }

    protected void sendAgreeReply(ACLMessage message) {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = createReply(message, ACLMessage.AGREE);
        send(replyMessage);
        log("Sends AGREE to %s", senderAID.getLocalName());
    }

    protected void sendAgreeReply(ACLMessage message, AgentAction agentAction)
            throws CodecException, OntologyException {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = createReply(message, ACLMessage.AGREE);
        Action action = new Action(senderAID, agentAction);
        getContentManager().fillContent(replyMessage, action);
        send(replyMessage);
        log("Sends AGREE of %s to %s", agentAction.getClass().getSimpleName(), senderAID.getLocalName());
    }

    protected void sendFailureReply(ACLMessage message) {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = createReply(message, ACLMessage.FAILURE);
        send(replyMessage);
        log("Sends FAILURE to %s", senderAID.getLocalName());
    }

    protected void sendFailureReply(ACLMessage message, String content) {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = createReply(message, ACLMessage.FAILURE);
        replyMessage.setContent(content);
        send(replyMessage);
        log("Sends FAILURE(%s) to %s", content, senderAID.getLocalName());
    }

    protected void sendFailureReply(ACLMessage message, AgentAction agentAction)
            throws CodecException, OntologyException {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = createReply(message, ACLMessage.FAILURE);
        Action action = new Action(senderAID, agentAction);
        getContentManager().fillContent(replyMessage, action);
        send(replyMessage);
        log("Sends FAILURE of %s to %s", agentAction.getClass().getSimpleName(), senderAID.getLocalName());
    }

    protected void sendFailureTo(AID receiverAID, String content) {
        ACLMessage message = createMessage(ACLMessage.FAILURE, receiverAID);
        message.setContent(content);
        send(message);
        log("Sends FAILURE(%s) to %s", content, receiverAID.getLocalName());
    }

    protected void sendInformTo(AID receiverAID, String content) {
        ACLMessage message = createMessage(ACLMessage.INFORM, receiverAID);
        message.setContent(content);
        send(message);
        log("Sends INFORM(%s) to %s", content, receiverAID.getLocalName());
    }

    protected void sendInformTo(AID receiverAID, AgentAction agentAction) throws CodecException, OntologyException {
        ACLMessage message = createMessage(ACLMessage.INFORM, receiverAID);
        Action action = new Action(receiverAID, agentAction);
        getContentManager().fillContent(message, action);
        send(message);
        log("Sends INFORM of %s to %s", agentAction.getClass().getSimpleName(), receiverAID.getLocalName());
    }

    protected void sendNotUnderstoodReply(ACLMessage message) {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = message.createReply();
        replyMessage.setPerformative(ACLMessage.NOT_UNDERSTOOD);
        send(replyMessage);
        log("Sends NOT_UNDERSTOOD to %s", senderAID.getLocalName());
    }

    protected void sendNotUnderstoodReply(ACLMessage message, AgentAction agentAction)
            throws CodecException, OntologyException {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = createReply(message, ACLMessage.NOT_UNDERSTOOD);
        Action action = new Action(senderAID, agentAction);
        getContentManager().fillContent(replyMessage, action);
        send(replyMessage);
        log("Sends NOT_UNDERSTOOD of %s to %s", agentAction.getClass().getSimpleName(), senderAID.getLocalName());
    }

    protected void sendRefuseReply(ACLMessage message) {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = message.createReply();
        replyMessage.setPerformative(ACLMessage.REFUSE);
        send(replyMessage);
        log("Sends REFUSE to %s", senderAID.getLocalName());
    }

    protected void sendRefuseReply(ACLMessage message, String content) {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = message.createReply();
        replyMessage.setContent(content);
        replyMessage.setPerformative(ACLMessage.REFUSE);
        send(replyMessage);
        log("Sends REFUSE to %s", senderAID.getLocalName());
    }

    protected void sendRefuseReply(ACLMessage message, AgentAction agentAction)
            throws CodecException, OntologyException {
        AID senderAID = message.getSender();
        ACLMessage replyMessage = createReply(message, ACLMessage.REFUSE);
        Action action = new Action(senderAID, agentAction);
        getContentManager().fillContent(replyMessage, action);
        send(replyMessage);
        log("Sends REFUSE to %s", senderAID.getLocalName());
    }

    protected <T> AID startAgent(String nickname, Class<T> agentClass, Object... args) throws StaleProxyException {
        nickname = nickname.toLowerCase();
        AgentContainer agentContainer = getContainerController();
        AgentController agentController = agentContainer.createNewAgent(nickname, agentClass.getName(), args);
        agentController.start();
        return new AID(agentController.getName(), true);
    }

    protected <T> AID startAgent(AgentContainer agentContainer, String nickname, Class<T> agentClass, Object... args)
            throws StaleProxyException {
        nickname = nickname.toLowerCase();
        AgentController agentController = agentContainer.createNewAgent(nickname, agentClass.getName(), args);
        agentController.start();
        return new AID(agentController.getName(), true);
    }

}