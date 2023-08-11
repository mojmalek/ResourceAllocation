package experiments;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import java.io.File;
import java.util.Date;

public class BasicExp {

    public static void main(String[] args) {

        String logFileName = "logs/" + "BasicExp-Agent0-" + new Date() + ".txt";
        File logFile = new File (logFileName);
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
//        p.setParameter(Profile.GUI, "true");
        ContainerController cc = rt.createMainContainer(p);
        int numberOfRounds = 1000;
        int numberOfAgents = 8;
        for(int i=0; i<=numberOfAgents; i++) {
            AgentController ac;
            try {
                if (i == 0) {
                    ac = cc.createNewAgent(numberOfAgents + "Agent0", "agents.MasterAgent", new Object[]{numberOfAgents, numberOfRounds, logFileName});
                    ac.start();
                } else {
                    ac = cc.createNewAgent(numberOfAgents + "Agent" + i, "agents.BasicAgent", new Object[]{numberOfAgents, i, numberOfRounds});
                    ac.start();
                }
//                ac.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }
    }
}
