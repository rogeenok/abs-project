package es.upm.woa.group4.util;

import jade.core.Agent;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class DirectoryRegistrar {

    public static void register(Agent agent, String serviceType) {
        register(agent, serviceType, null);
    }

    public static void register(Agent agent, String serviceType, String owner) {
        try {
            DFAgentDescription agentDescription = new DFAgentDescription();
            ServiceDescription serviceDescription = new ServiceDescription();
            serviceDescription.setName(agent.getName());
            serviceDescription.setType(serviceType);
            if (owner != null) {
                serviceDescription.setOwnership(owner);
            }
            agentDescription.addServices(serviceDescription);
            DFService.register(agent, agentDescription);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

}