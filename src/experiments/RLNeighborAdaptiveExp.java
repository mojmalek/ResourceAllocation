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
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.util.SupplierUtil;

import java.io.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;

public class RLNeighborAdaptiveExp {


    public static void main(String[] args) {
        try {
//            runSimulation1();
//            smallWorldSim();
            scaleFreeSim();
//            randomSim();
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


    public static void smallWorldSim() throws StaleProxyException {

        int numberOfAgents = 10;
        long duration = 60000;
        long currentTime, endTime;
        SimulationEngine simulationEngine1, simulationEngine2;
        Set<AgentController> agentControllers = new HashSet<>();

        String agentType1 = "SmallWorld-A";
        String agentType2 = "SmallWorld-NoCasA";

        String resultFileName1 = "logs/results/" + agentType1 + "-" + new Date() + ".txt";
        String resultFileName2 = "logs/results/" + agentType2 + "-" + new Date() + ".txt";

        for (int param = 4; param <= 16; param+=2) {
            logResults(resultFileName1, "");
            logResults(resultFileName1, "param = " + param);
            logResults(resultFileName1, "");
            logResults(resultFileName2, "");
            logResults(resultFileName2, "param = " + param);
            logResults(resultFileName2, "");
            simulationEngine1 = new SimulationEngine( param, agentType1);
            simulationEngine2 = new SimulationEngine( param, agentType2);
            for (int exp = 1; exp <= 20; exp++) {
                String logFileNameMaster1 = "logs/" + "Master-" + agentType1 + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameMaster2 = "logs/" + "Master-" + agentType2 + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll1 = "logs/" + "All-" + agentType1  + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll2 = "logs/" + "All-" + agentType2  + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

//              double connectivity = 0.0;
//              Integer[][] adjacency = simulationEngine.generateRandomAdjacencyMatrix(numberOfAgents, connectivity);

                Supplier<String> vSupplier = new Supplier<String>() {
                    private int id = 1;
                    @Override
                    public String get() {
                        return "" + id++;
                    }
                };

                Graph<String, DefaultWeightedEdge> smallWorldGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
                // Small-world graph
                WattsStrogatzGraphGenerator<String, DefaultWeightedEdge> smallWorldGraphGenerator = new WattsStrogatzGraphGenerator<>(numberOfAgents, 2, 0.05);
                smallWorldGraphGenerator.generateGraph(smallWorldGraph);

                System.out.println("Small-world:");
                Iterator<String> iter1 = new DepthFirstIterator<>(smallWorldGraph);
                while (iter1.hasNext()) {
                    String vertex = iter1.next();
                    System.out.println(vertex + " is connected to: " + smallWorldGraph.edgesOf(vertex).toString());
                }

                Integer[][] smallWorldAdjacency = simulationEngine1.generateAdjacencyMatrixFromGraph(smallWorldGraph, numberOfAgents);

                //TODO: save the social network array in a text file in order to re-use it.
//              Integer[][] adjacency = {{null, 1, 1, null, null, null, 1, 1},
//                                     {1, null, 1, null, 1, null, null, 1},
//                                     {1, 1, null, 1, null, null, 1, null},
//                                     {null, null, 1, null, 1, null, null, 1},
//                                     {null, 1, null, 1, null, 1, null, 1},
//                                     {null, null, null, null, 1, null, 1, null},
//                                     {1, null, 1, null, null, 1, null, 1},
//                                     {1, 1, null, 1, 1, null, 1, null}};

//                System.out.println("Agent social network adjacency matrix: ");
//                for (int i = 0; i < adjacency.length; i++) {
//                    for (int j = 0; j < adjacency[i].length; j++) {
//                        if (adjacency[i][j] == null) {
//                            System.out.print("0, ");
//                        } else {
//                            System.out.print(adjacency[i][j] + ", ");
//                        }
//                    }
//                    System.out.println();
//                }
                System.out.println();

                agentControllers.clear();
                currentTime = System.currentTimeMillis();
                endTime = currentTime + duration;
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController1, agentController2;
                    try {
                        if (i == 0) {
                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.TimedMasterAgent", new Object[]{numberOfAgents, endTime, smallWorldGraph, smallWorldAdjacency, logFileNameMaster1, resultFileName1, agentType1});
                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.TimedMasterAgent", new Object[]{numberOfAgents, endTime, smallWorldGraph, smallWorldAdjacency, logFileNameMaster2, resultFileName2, agentType2});
                            agentController2.start();
                        } else {
                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.TimedSocialAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, smallWorldAdjacency[i - 1], logFileNameAll1, simulationEngine1, true, agentType1});
                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.TimedSocialAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, smallWorldAdjacency[i - 1], logFileNameAll2, simulationEngine2, false, agentType2});
                            agentController2.start();
                        }
                        agentControllers.add( agentController1);
                        agentControllers.add( agentController2);
                    } catch (StaleProxyException e) {
                        e.printStackTrace();
                    }
                }

                while (currentTime < endTime + 1500) {
                    currentTime = System.currentTimeMillis();
                }
                for( AgentController agentController : agentControllers) {
                    agentController.kill();
                }
//                containerController.kill();
            }
        }
    }


    public static void scaleFreeSim() throws StaleProxyException {

        int numberOfAgents = 10;
        int numberOfRounds = 1000;
        long duration = 60000;
        long currentTime, endTime;
        SimulationEngine simulationEngine1, simulationEngine2;
        Set<AgentController> agentControllers = new HashSet<>();

        String agentType1 = "ScaleFree-NoCas-RL-A";
        String agentType2 = "ScaleFree-NoCas-A";

//        String resultFileName1 = "logs/results/" + agentType1 + "-" + new Date() + ".txt";
        String resultFileName2 = "logs/results/" + agentType2 + "-" + new Date() + ".txt";

        for (long resource = 16; resource <= 16; resource+=2) {
//            logResults(resultFileName1, "");
//            logResults(resultFileName1, "resource = " + resource);
//            logResults(resultFileName1, "");
            logResults(resultFileName2, "");
            logResults(resultFileName2, "resource = " + resource);
            logResults(resultFileName2, "");
            simulationEngine1 = new SimulationEngine( resource, agentType1);
            simulationEngine2 = new SimulationEngine( resource, agentType2);
            for (int exp = 1; exp <= 1; exp++) {
//                String logFileNameMaster1 = "logs/" + "Master-" + agentType1 + "-resource=" + resource + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameMaster2 = "logs/" + "Master-" + agentType2 + "-resource=" + resource + "-exp" + exp + "-" + new Date() + ".txt";
//                String logFileNameAll1 = "logs/" + "All-" + agentType1  + "-resource=" + resource + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll2 = "logs/" + "All-" + agentType2  + "-resource=" + resource + "-exp" + exp + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

//              double connectivity = 0.0;
//              Integer[][] adjacency = simulationEngine.generateRandomAdjacencyMatrix(numberOfAgents, connectivity);

                Supplier<String> vSupplier = new Supplier<String>() {
                    private int id = 1;
                    @Override
                    public String get() {
                        return "" + id++;
                    }
                };

                Graph<String, DefaultWeightedEdge> scaleFreeGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
                // Scale-free graph
                ScaleFreeGraphGenerator<String, DefaultWeightedEdge> scaleFreeGraphGenerator = new ScaleFreeGraphGenerator<>(numberOfAgents);
                scaleFreeGraphGenerator.generateGraph(scaleFreeGraph);

                System.out.println("Scale-free:");
                Iterator<String> iter2 = new DepthFirstIterator<>(scaleFreeGraph);
                while (iter2.hasNext()) {
                    String vertex = iter2.next();
                    System.out.println(vertex + " is connected to: " + scaleFreeGraph.edgesOf(vertex).toString());
                }

                Integer[][] scaleFreeAdjacency = simulationEngine1.generateAdjacencyMatrixFromGraph(scaleFreeGraph, numberOfAgents);

                //TODO: save the social network array in a text file in order to re-use it.
//              Integer[][] adjacency = {{null, 1, 1, null, null, null, 1, 1},
//                                     {1, null, 1, null, 1, null, null, 1},
//                                     {1, 1, null, 1, null, null, 1, null},
//                                     {null, null, 1, null, 1, null, null, 1},
//                                     {null, 1, null, 1, null, 1, null, 1},
//                                     {null, null, null, null, 1, null, 1, null},
//                                     {1, null, 1, null, null, 1, null, 1},
//                                     {1, 1, null, 1, 1, null, 1, null}};

//                System.out.println("Agent social network adjacency matrix: ");
//                for (int i = 0; i < adjacency.length; i++) {
//                    for (int j = 0; j < adjacency[i].length; j++) {
//                        if (adjacency[i][j] == null) {
//                            System.out.print("0, ");
//                        } else {
//                            System.out.print(adjacency[i][j] + ", ");
//                        }
//                    }
//                    System.out.println();
//                }
                System.out.println();

                agentControllers.clear();
                currentTime = System.currentTimeMillis();
                endTime = currentTime + duration;
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController1, agentController2;
                    try {
                        if (i == 0) {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.RLMasterAgent", new Object[]{numberOfAgents, endTime, scaleFreeGraph, scaleFreeAdjacency, logFileNameMaster1, resultFileName1, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.RLMasterAgent", new Object[]{numberOfAgents, numberOfRounds, logFileNameMaster2, resultFileName2, agentType2});
                            agentController2.start();
                        } else {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.RLNeighborAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, scaleFreeAdjacency[i - 1], logFileNameAll1, simulationEngine1, true, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.RLNeighborAdaptiveAgent", new Object[]{numberOfAgents, i, numberOfRounds, scaleFreeAdjacency[i - 1], logFileNameAll2, simulationEngine2, agentType2});
                            agentController2.start();
                        }
//                        agentControllers.add( agentController1);
                        agentControllers.add( agentController2);
                    } catch (StaleProxyException e) {
                        e.printStackTrace();
                    }
                }

                while (currentTime < endTime + 1500) {
                    currentTime = System.currentTimeMillis();
                }
                for( AgentController agentController : agentControllers) {
                    agentController.kill();
                }
//                containerController.kill();
            }
        }
    }


    public static void randomSim() throws StaleProxyException {

        int numberOfAgents = 10;
        long duration = 60000;
        long currentTime, endTime;
        SimulationEngine simulationEngine1, simulationEngine2;
        Set<AgentController> agentControllers = new HashSet<>();

        String agentType1 = "Random-A";
        String agentType2 = "Random-NoCasA";

        String resultFileName1 = "logs/results/" + agentType1 + "-" + new Date() + ".txt";
        String resultFileName2 = "logs/results/" + agentType2 + "-" + new Date() + ".txt";

        for (int degree = 2; degree <= 2; degree+=2) {
            logResults(resultFileName1, "");
            logResults(resultFileName1, "degree = " + degree);
            logResults(resultFileName1, "");
            logResults(resultFileName2, "");
            logResults(resultFileName2, "degree = " + degree);
            logResults(resultFileName2, "");
            simulationEngine1 = new SimulationEngine( 16, agentType1);
            simulationEngine2 = new SimulationEngine( 16, agentType2);
            for (int exp = 1; exp <= 20; exp++) {
                String logFileNameMaster1 = "logs/" + "Master-" + agentType1 + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameMaster2 = "logs/" + "Master-" + agentType2 + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll1 = "logs/" + "All-" + agentType1  + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll2 = "logs/" + "All-" + agentType2  + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

//              double connectivity = 0.0;
//              Integer[][] adjacency = simulationEngine.generateRandomAdjacencyMatrix(numberOfAgents, connectivity);

                int numberOfEdges = degree * numberOfAgents / 2;
                Integer[][] randomAdjacency = simulationEngine1.generateRandomAdjacencyMatrix2(numberOfAgents, numberOfEdges);

//                Supplier<String> vSupplier = new Supplier<String>() {
//                    private int id = 1;
//                    @Override
//                    public String get() {
//                        return "" + id++;
//                    }
//                };

//                Graph<String, DefaultWeightedEdge> randomGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
                // Random graph
//                GnmRandomGraphGenerator<String, DefaultWeightedEdge> randomGraphGenerator = new GnmRandomGraphGenerator<>( numberOfAgents, degree * numberOfAgents / 2);
//                randomGraphGenerator.generateGraph(randomGraph);

                System.out.println("Random:");
//                Iterator<String> iter1 = new DepthFirstIterator<>(randomGraph);
//                while (iter1.hasNext()) {
//                    String vertex = iter1.next();
//                    System.out.println(vertex + " is connected to: " + randomGraph.edgesOf(vertex).toString());
//                }

//                Integer[][] randomAdjacency = simulationEngine1.generateAdjacencyMatrixFromGraph(randomGraph, numberOfAgents);

                //TODO: save the social network array in a text file in order to re-use it.
//              Integer[][] adjacency = {{null, 1, 1, null, null, null, 1, 1},
//                                     {1, null, 1, null, 1, null, null, 1},
//                                     {1, 1, null, 1, null, null, 1, null},
//                                     {null, null, 1, null, 1, null, null, 1},
//                                     {null, 1, null, 1, null, 1, null, 1},
//                                     {null, null, null, null, 1, null, 1, null},
//                                     {1, null, 1, null, null, 1, null, 1},
//                                     {1, 1, null, 1, 1, null, 1, null}};

                System.out.println("Agent social network adjacency matrix: ");
                for (int i = 0; i < randomAdjacency.length; i++) {
                    for (int j = 0; j < randomAdjacency[i].length; j++) {
                        if (randomAdjacency[i][j] == null) {
                            System.out.print("0, ");
                        } else {
                            System.out.print(randomAdjacency[i][j] + ", ");
                        }
                    }
                    System.out.println();
                }
                System.out.println();

                agentControllers.clear();
                currentTime = System.currentTimeMillis();
                endTime = currentTime + duration;
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController1, agentController2;
                    try {
                        if (i == 0) {
                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.TimedMasterAgent", new Object[]{numberOfAgents, endTime, null, randomAdjacency, logFileNameMaster1, resultFileName1, agentType1});
                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.TimedMasterAgent", new Object[]{numberOfAgents, endTime, null, randomAdjacency, logFileNameMaster2, resultFileName2, agentType2});
                            agentController2.start();
                        } else {
                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.TimedSocialAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, randomAdjacency[i - 1], logFileNameAll1, simulationEngine1, true, agentType1});
                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.TimedSocialAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, randomAdjacency[i - 1], logFileNameAll2, simulationEngine2, false, agentType2});
                            agentController2.start();
                        }
                        agentControllers.add( agentController1);
                        agentControllers.add( agentController2);
                    } catch (StaleProxyException e) {
                        e.printStackTrace();
                    }
                }

                while (currentTime < endTime + 1500) {
                    currentTime = System.currentTimeMillis();
                }
                for( AgentController agentController : agentControllers) {
                    agentController.kill();
                }
//                containerController.kill();
            }
        }
    }


    protected static void logResults(String resultFileName, String msg) {

        System.out.println(msg);

        try {
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(resultFileName, true)));
            out.println(msg);
            out.close();
        } catch (IOException e) {
            System.err.println("Error writing file..." + e.getMessage());
        }
    }
}
