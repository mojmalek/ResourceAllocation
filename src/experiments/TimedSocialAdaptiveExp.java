package experiments;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import model.SimulationEngine;

import java.io.File;
import java.util.Date;

public class TimedSocialAdaptiveExp {


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

        SimulationEngine simulationEngine = new SimulationEngine();

        String logFileName1 = "logs/" + "TimedSocialAdaptiveExp-Master-" + new Date() + ".txt";
        String logFileName2 = "logs/" + "TimedSocialAdaptiveExp-All-" + new Date() + ".txt";
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
//        p.setParameter(Profile.GUI, "true");
        ContainerController cc = rt.createMainContainer(p);

        long currentTime = System.currentTimeMillis();
        long endTime = currentTime + 60000;
        int numberOfAgents = 8;
        double connectivity = 0.4;

//        Integer[][] socialNetwork = simulationEngine.generateSocialNetwork(numberOfAgents, connectivity);

//        Integer[][] distances = simulationEngine.computeDistances( socialNetwork);

        //TODO: save the social network array in a text file in order to re-use it.

        Integer[][] socialNetwork = {{null, 1, 1, null, null, null, 1, 1},
                                     {1, null, 1, null, 1, null, null, 1},
                                     {1, 1, null, 1, null, null, 1, null},
                                     {null, null, 1, null, 1, null, null, 1},
                                     {null, 1, null, 1, null, 1, null, 1},
                                     {null, null, null, null, 1, null, 1, null},
                                     {1, null, 1, null, null, 1, null, 1},
                                     {1, 1, null, 1, 1, null, 1, null}};

//                0, 1, 1, 0, 0, 0, 1, 1,
//                1, 0, 1, 0, 1, 0, 0, 1,
//                1, 1, 0, 1, 0, 0, 1, 0,
//                0, 0, 1, 0, 1, 0, 0, 1,
//                0, 1, 0, 1, 0, 1, 0, 1,
//                0, 0, 0, 0, 1, 0, 1, 0,
//                1, 0, 1, 0, 0, 1, 0, 1,
//                1, 1, 0, 1, 1, 0, 1, 0,

                System.out.println("Agent social network adjacency matrix: ");
        for (int i=0; i<socialNetwork.length; i++) {
            for (int j=0; j<socialNetwork[i].length; j++) {
                if(socialNetwork[i][j] == null) {
                    System.out.print("0, ");
                } else {
                    System.out.print(socialNetwork[i][j] + ", ");
                }
            }
            System.out.println();
        }

        System.out.println();

        for (int i = 0; i <= numberOfAgents; i++) {
            AgentController ac;
            try {
                if (i == 0) {
                    ac = cc.createNewAgent(numberOfAgents + "Agent0", "agents.TimedMasterAgent", new Object[]{numberOfAgents, endTime, socialNetwork, logFileName1});
                    ac.start();
                } else {
                    ac = cc.createNewAgent(numberOfAgents + "Agent" + i, "agents.TimedSocialAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, socialNetwork[i-1], logFileName2});
                    ac.start();
                }
//                ac.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }
    }
}
