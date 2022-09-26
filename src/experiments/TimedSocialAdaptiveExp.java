package experiments;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import model.SimulationEngine;
import org.jgrapht.Graph;
import org.jgrapht.generate.ScaleFreeGraphGenerator;
import org.jgrapht.generate.WattsStrogatzGraphGenerator;
import org.jgrapht.generate.WindmillGraphsGenerator;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.util.SupplierUtil;

import java.io.File;
import java.util.Date;
import java.util.Iterator;
import java.util.function.Supplier;

import static org.jgrapht.generate.WindmillGraphsGenerator.Mode.DUTCHWINDMILL;

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

//        double connectivity = 0.0;
//        Integer[][] adjacency = simulationEngine.generateRandomAdjacencyMatrix(numberOfAgents, connectivity);

        Supplier<String> vSupplier = new Supplier<String>() {
            private int id = 1;
            @Override
            public String get() {
                return "A" + id++;
            }
        };

        Graph<String, DefaultWeightedEdge> graph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());

        // Small-world graph
//        WattsStrogatzGraphGenerator<String, DefaultWeightedEdge> graphGenerator = new WattsStrogatzGraphGenerator<>(numberOfAgents, 2, 0.4);

        // Scale-free graph
//        ScaleFreeGraphGenerator<String, DefaultWeightedEdge> graphGenerator = new ScaleFreeGraphGenerator<>(numberOfAgents);

        // Friendship graph
        WindmillGraphsGenerator<String, DefaultWeightedEdge> graphGenerator = new WindmillGraphsGenerator<>(DUTCHWINDMILL, 10, 3);

        graphGenerator.generateGraph(graph);
        numberOfAgents = graph.vertexSet().size();

        Iterator<String> iter = new DepthFirstIterator<>(graph);
        while (iter.hasNext()) {
            String vertex = iter.next();
            System.out.println(vertex + " is connected to: " + graph.edgesOf(vertex).toString());
        }

        Integer[][] adjacency = simulationEngine.generateAdjacencyMatrixFromGraph(graph, numberOfAgents);

        //TODO: save the social network array in a text file in order to re-use it.
//        Integer[][] adjacency = {{null, 1, 1, null, null, null, 1, 1},
//                                     {1, null, 1, null, 1, null, null, 1},
//                                     {1, 1, null, 1, null, null, 1, null},
//                                     {null, null, 1, null, 1, null, null, 1},
//                                     {null, 1, null, 1, null, 1, null, 1},
//                                     {null, null, null, null, 1, null, 1, null},
//                                     {1, null, 1, null, null, 1, null, 1},
//                                     {1, 1, null, 1, 1, null, 1, null}};


        System.out.println("Agent social network adjacency matrix: ");
        for (int i=0; i<adjacency.length; i++) {
            for (int j=0; j<adjacency[i].length; j++) {
                if(adjacency[i][j] == null) {
                    System.out.print("0, ");
                } else {
                    System.out.print(adjacency[i][j] + ", ");
                }
            }
            System.out.println();
        }

        System.out.println();

        for (int i = 0; i <= numberOfAgents; i++) {
            AgentController ac;
            try {
                if (i == 0) {
                    ac = cc.createNewAgent("A0", "agents.TimedMasterAgent", new Object[]{numberOfAgents, endTime, graph, adjacency, logFileName1});
                    ac.start();
                } else {
                    ac = cc.createNewAgent("A" + i, "agents.TimedSocialAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, adjacency[i-1], logFileName2});
                    ac.start();
                }
//                ac.start();
            } catch (StaleProxyException e) {
                e.printStackTrace();
            }
        }
    }
}
