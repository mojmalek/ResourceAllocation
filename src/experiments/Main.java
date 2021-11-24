package experiments;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

import java.sql.Array;
import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {

        Runtime rt=Runtime.instance();
        Profile p=new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
//        p.setParameter(Profile.GUI, "true");
        ContainerController cc=rt.createMainContainer(p);
        int numberOfAgents = 2;
        for(int i=1; i<=numberOfAgents; i++){
            AgentController ac;
            try {
//                Object[] arguments = new Object[]{numberOfAgents, i};
                ac=cc.createNewAgent("Agent"+i, "model.ResourceAllocationAgent", new Object[]{numberOfAgents, i});
                ac.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }
    }
}
