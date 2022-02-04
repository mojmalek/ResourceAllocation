package experiments;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public class AdaptiveExp {

    public static void main(String[] args) {

        Runtime rt=Runtime.instance();
        Profile p=new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
//        p.setParameter(Profile.GUI, "true");
        ContainerController cc=rt.createMainContainer(p);
        int numberOfAgents = 2;
        for(int i=0; i<=numberOfAgents; i++) {
            AgentController ac;
            try {
                if (i == 0) {
                    ac = cc.createNewAgent("Agent0", "model.MasterAgent", new Object[]{numberOfAgents});
                    ac.start();
                } else {
                    ac = cc.createNewAgent("Agent" + i, "model.AdaptiveAgent", new Object[]{numberOfAgents, i});
                    ac.start();
                }
//                ac.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }
    }
}
