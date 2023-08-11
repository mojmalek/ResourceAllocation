package experiments;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import java.io.File;
import java.util.Date;

public class AdaptiveExp {


    public static void main(String[] args) {
        try {
//            runSimulation1();
            runSimulation2();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void runSimulation1() {

        String logFileName = "logs/" + "AdaptiveExp-Agent0-" + new Date() + ".txt";
        File logFile = new File (logFileName);
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
//        p.setParameter(Profile.GUI, "true");
        ContainerController cc = rt.createMainContainer(p);
        int numberOfRounds = 1000;
        for (int numberOfAgents=4; numberOfAgents<65; numberOfAgents=numberOfAgents*2) {
            for (int i = 0; i <= numberOfAgents; i++) {
                AgentController ac;
                try {
                    if (i == 0) {
                        ac = cc.createNewAgent(numberOfAgents + "Agent0", "agents.MasterAgent", new Object[]{numberOfAgents, numberOfRounds, logFileName});
                        ac.start();
                    } else {
                        ac = cc.createNewAgent(numberOfAgents + "Agent" + i, "agents.AdaptiveAgent", new Object[]{numberOfAgents, i, numberOfRounds});
                        ac.start();
                    }
//                ac.start();
                } catch (StaleProxyException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    public static void runSimulation2() {

        String logFileName = "logs/" + "AdaptiveExp-Agent0-" + new Date() + ".txt";
        File logFile = new File (logFileName);
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
//        p.setParameter(Profile.GUI, "true");
        ContainerController cc = rt.createMainContainer(p);
        int numberOfRounds = 1000;
        int numberOfAgents = 48;
        for (int i = 0; i <= numberOfAgents; i++) {
            AgentController ac;
            try {
                if (i == 0) {
                    ac = cc.createNewAgent(numberOfAgents + "Agent0", "agents.MasterAgent", new Object[]{numberOfAgents, numberOfRounds, logFileName});
                    ac.start();
                } else {
                    ac = cc.createNewAgent(numberOfAgents + "Agent" + i, "agents.AdaptiveAgent", new Object[]{numberOfAgents, i, numberOfRounds});
                    ac.start();
                }
//                ac.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }
    }
}
