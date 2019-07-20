package es.upm.woa.group4.agent;

import es.upm.woa.group4.util.Log;
import jade.core.AID;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.core.behaviours.OneShotBehaviour;
import jade.util.leap.Properties;
import jade.wrapper.AgentContainer;
import jade.wrapper.StaleProxyException;

import static es.upm.woa.group4.common.Constants.*;

public class AgPlatform extends AgBase {

    private static final String CONTAINER_NAME = "WoA";
    private static final String TAG = "Platform";

    @Override
    protected String getServiceType() {
        return SERVICE_TYPE_PLATFORM;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void setup() {
        super.setup();
        addBehaviour(new BootBehaviour());
        Log.info(TAG, "Activated");
    }

    @Override
    protected void takeDown() {
        Log.info(TAG, "Exited");
    }

    private class BootBehaviour extends OneShotBehaviour {

        @Override
        public void action() {
            AgentContainer agentContainer = bootAgentContainer();
            try {
                bootWorldAgent(agentContainer);
                bootRegDeskAgent(agentContainer);
                bootTribeAgents(agentContainer);
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }

        private AID bootWorldAgent(AgentContainer agentContainer) throws StaleProxyException {
            return startAgent(agentContainer, SERVICE_TYPE_WORLD, AgWorld.class);
        }

        private AID bootRegDeskAgent(AgentContainer agentContainer) throws StaleProxyException {
            return startAgent(agentContainer, SERVICE_TYPE_REGISTRATION_DESK, AgRegistrationDesk.class);
        }

        private void bootTribeAgents(AgentContainer agentContainer) throws StaleProxyException {
            // group1 tribe
            //startAgent(agentContainer, "Tribe1", es.upm.woa.group1.agent.AgTribe.class);
            // group2 tribe
            //startAgent(agentContainer, "Tribe2", es.upm.woa.group2.agent.AgTribe.class);
            // group3 tribe
            startAgent(agentContainer,"Tribe3", es.upm.woa.group3.agent.AgTribe.class);
            // group4 tribe - OURS
            startAgent(agentContainer, "Tribe4", es.upm.woa.group4.agent.AgTribe.class);
            //startAgent(agentContainer, "Tribe7", es.upm.woa.group4.agent.AgTribe.class);
            // group5 tribe
            startAgent(agentContainer, "Tribe5", es.upm.woa.group5.agent.AgTribe.class);
        }

        private AgentContainer bootAgentContainer() {
            Properties props = new Properties();
            props.setProperty(Profile.CONTAINER_NAME, CONTAINER_NAME);
            props.setProperty(Profile.GUI, "true");
            props.setProperty(Profile.MAIN, "false");
            props.setProperty(Profile.NO_MTP, "true");
            ProfileImpl profile = new ProfileImpl(props);
            return Runtime.instance().createAgentContainer(profile);
        }
    }

}