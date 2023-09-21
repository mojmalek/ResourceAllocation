package experiments;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import model.SimulationEngine;
import org.jgrapht.Graph;
import org.jgrapht.generate.CompleteGraphGenerator;
import org.jgrapht.generate.ScaleFreeGraphGenerator;
import org.jgrapht.generate.WattsStrogatzGraphGenerator;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.jgrapht.nio.GraphExporter;
import org.jgrapht.nio.GraphImporter;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;
import org.jgrapht.traverse.DepthFirstIterator;
import org.jgrapht.util.SupplierUtil;

import java.io.*;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Supplier;

public class DeepRLNeighborAdaptiveExp {


    public static void main(String[] args) {
        try {
//            completeSim();
//            smallWorldSim();
            scaleFreeSim();
//            randomSim();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void completeSim() throws StaleProxyException {

        int numberOfAgents = 8;
        int numberOfRounds = 2000;
//        long duration = 600000;
//        long currentTime, endTime;
        SimulationEngine simulationEngine1, simulationEngine2;
        Set<AgentController> agentControllers = new HashSet<>();

        String agentType1 = "Complete-NoCas-RL-A";
        String agentType2 = "Complete-NoCas-A";

//        String resultFileName1 = "logs/results/" + agentType1 + "-" + new Date() + ".txt";
        String resultFileName2 = "logs/results/" + agentType2 + "-" + new Date() + ".txt";

        for (long param = 8; param <= 8; param+=2) {
//            logResults(resultFileName1, "");
//            logResults(resultFileName1, "param = " + param);
//            logResults(resultFileName1, "");
//            logResults(resultFileName2, "");
//            logResults(resultFileName2, "param = " + param);
//            logResults(resultFileName2, "");
            simulationEngine1 = new SimulationEngine( param, agentType1);
            simulationEngine2 = new SimulationEngine( param, agentType2);
            for (int exp = 1; exp <= 1; exp++) {
//                String logFileNameMaster1 = "logs/" + "Master-" + agentType1 + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameMaster2 = "logs/" + "Master-" + agentType2 + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
//                String logFileNameAll1 = "logs/" + "All-" + agentType1  + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll2 = "logs/" + "All-" + agentType2  + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

                Supplier<String> vSupplier = new Supplier<String>() {
                    private int id = 1;
                    @Override
                    public String get() {
                        return "" + id++;
                    }
                };

                Graph<String, DefaultWeightedEdge> completeGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
                // Complete graph
                CompleteGraphGenerator<String, DefaultWeightedEdge> completeGraphGenerator = new CompleteGraphGenerator<>(numberOfAgents);
                completeGraphGenerator.generateGraph(completeGraph);

                System.out.println("Complete:");
                Iterator<String> iter2 = new DepthFirstIterator<>(completeGraph);
                while (iter2.hasNext()) {
                    String vertex = iter2.next();
                    System.out.println(vertex + " is connected to: " + completeGraph.edgesOf(vertex).toString());
                }

                Integer[][] completeAdjacency = simulationEngine1.generateAdjacencyMatrixFromGraph(completeGraph, numberOfAgents);

                System.out.println("Agent social network adjacency matrix: ");
                System.out.print("{");
                for (int i = 0; i < completeAdjacency.length; i++) {
                    System.out.print("{");
                    for (int j = 0; j < completeAdjacency[i].length; j++) {
                        if (completeAdjacency[i][j] == null) {
                            System.out.print("null, ");
                        } else {
                            System.out.print(completeAdjacency[i][j] + ", ");
                        }
                    }
                    System.out.println("}");
                }
                System.out.print("}");
                System.out.println();

                agentControllers.clear();
//                currentTime = System.currentTimeMillis();
//                endTime = currentTime + duration;
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController1, agentController2;
                    try {
                        if (i == 0) {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, endTime, completeGraph, completeAdjacency, logFileNameMaster1, resultFileName1, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, numberOfRounds, completeAdjacency, logFileNameMaster2, resultFileName2, agentType2});
                            agentController2.start();
                        } else {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.RLNeighborAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, completeAdjacency[i - 1], logFileNameAll1, simulationEngine1, true, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.RLNeighborAdaptiveAgent", new Object[]{numberOfAgents, i, numberOfRounds, completeAdjacency[i - 1], logFileNameAll2, simulationEngine2, agentType2});
                            agentController2.start();
                        }
//                        agentControllers.add( agentController1);
                        agentControllers.add( agentController2);
                    } catch (StaleProxyException e) {
                        e.printStackTrace();
                    }
                }

//                while (currentTime < endTime + 1500) {
//                    currentTime = System.currentTimeMillis();
//                }
//                for( AgentController agentController : agentControllers) {
//                    agentController.kill();
//                }
//                containerController.kill();
            }
        }
    }


    public static void smallWorldSim() throws StaleProxyException {

        int numberOfAgents = 8;
        int numberOfRounds = 20000;
//        long duration = 600000;
//        long currentTime, endTime;
        SimulationEngine simulationEngine1, simulationEngine2;
        Set<AgentController> agentControllers = new HashSet<>();

        String agentType1 = "SmallWorld-NoCas-RL-A";
        String agentType2 = "SmallWorld-NoCas-A";

//        String resultFileName1 = "logs/results/" + agentType1 + "-" + new Date() + ".txt";
        String resultFileName2 = "logs/results/" + agentType2 + "-" + new Date() + ".txt";

        for (long param = 8; param <= 8; param+=2) {
//            logResults(resultFileName1, "");
//            logResults(resultFileName1, "param = " + param);
//            logResults(resultFileName1, "");
//            logResults(resultFileName2, "");
//            logResults(resultFileName2, "param = " + param);
//            logResults(resultFileName2, "");
            simulationEngine1 = new SimulationEngine( param, agentType1);
            simulationEngine2 = new SimulationEngine( param, agentType2);
            for (int exp = 1; exp <= 1; exp++) {
//                String logFileNameMaster1 = "logs/" + "Master-" + agentType1 + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameMaster2 = "logs/" + "Master-" + agentType2 + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
//                String logFileNameAll1 = "logs/" + "All-" + agentType1  + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll2 = "logs/" + "All-" + agentType2  + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

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
//              Integer[][] smallWorldAdjacency = {{null, 1, 1, null, null, null, 1, 1},
//                                     {1, null, 1, null, 1, null, null, 1},
//                                     {1, 1, null, 1, null, null, 1, null},
//                                     {null, null, 1, null, 1, null, null, 1},
//                                     {null, 1, null, 1, null, 1, null, 1},
//                                     {null, null, null, null, 1, null, 1, null},
//                                     {1, null, 1, null, null, 1, null, 1},
//                                     {1, 1, null, 1, 1, null, 1, null}};

//                System.out.println("Agent social network adjacency matrix: ");
//                for (int i = 0; i < smallWorldAdjacency.length; i++) {
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
//                currentTime = System.currentTimeMillis();
//                endTime = currentTime + duration;
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController1, agentController2;
                    try {
                        if (i == 0) {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, endTime, smallWorldGraph, smallWorldAdjacency, logFileNameMaster1, resultFileName1, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, numberOfRounds, smallWorldAdjacency, logFileNameMaster2, resultFileName2, agentType2});
                            agentController2.start();
                        } else {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.RLNeighborAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, smallWorldAdjacency[i - 1], logFileNameAll1, simulationEngine1, true, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.RLNeighborAdaptiveAgent", new Object[]{numberOfAgents, i, numberOfRounds, smallWorldAdjacency[i - 1], logFileNameAll2, simulationEngine2, agentType2});
                            agentController2.start();
                        }
//                        agentControllers.add( agentController1);
                        agentControllers.add( agentController2);
                    } catch (StaleProxyException e) {
                        e.printStackTrace();
                    }
                }

//                while (currentTime < endTime + 1500) {
//                    currentTime = System.currentTimeMillis();
//                }
//                for( AgentController agentController : agentControllers) {
//                    agentController.kill();
//                }
//                containerController.kill();
            }
        }
    }


    public static void scaleFreeSim() throws StaleProxyException, IOException {

        boolean loadGraph = true;
        int numberOfAgents = 40;
        int numberOfRounds = 5000;
//        long duration = 600000;
//        long currentTime, endTime;
        SimulationEngine simulationEngine1, simulationEngine2;
        Set<AgentController> agentControllers = new HashSet<>();

        String agentType1 = "ScaleFree-NoCas-RL-A";
        String agentType2 = "ScaleFree-NoCas-A";

//        String resultFileName1 = "logs/results/" + agentType1 + "-" + new Date() + ".txt";
        String resultFileName2 = "logs/results/" + agentType2 + "-" + new Date() + ".txt";

        for (long param = 8; param <= 8; param+=2) {
//            logResults(resultFileName1, "");
//            logResults(resultFileName1, "param = " + param);
//            logResults(resultFileName1, "");
//            logResults(resultFileName2, "");
//            logResults(resultFileName2, "param = " + param);
//            logResults(resultFileName2, "");
            simulationEngine1 = new SimulationEngine( param, agentType1);
            simulationEngine2 = new SimulationEngine( param, agentType2);
            for (int exp = 1; exp <= 1; exp++) {
//                String logFileNameMaster1 = "logs/" + "Master-" + agentType1 + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameMaster2 = "logs/" + "Master-" + agentType2 + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
//                String logFileNameAll1 = "logs/" + "All-" + agentType1  + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll2 = "logs/" + "All-" + agentType2  + "-param=" + param + "-exp" + exp + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

                Supplier<String> vSupplier = new Supplier<String>() {
                    private int id = 1;
                    @Override
                    public String get() {
                        return "" + id++;
                    }
                };

                Graph<String, DefaultWeightedEdge> scaleFreeGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
                if (loadGraph) {
                    GraphImporter<String, DefaultWeightedEdge> importer = new DOTImporter<>();
                    Reader reader = new FileReader("scaleFreeGraph.dot");
                    importer.importGraph(scaleFreeGraph, reader);
                    reader.close();
                } else {
                    ScaleFreeGraphGenerator<String, DefaultWeightedEdge> scaleFreeGraphGenerator = new ScaleFreeGraphGenerator<>(numberOfAgents);
                    scaleFreeGraphGenerator.generateGraph(scaleFreeGraph);

                    GraphExporter<String, DefaultWeightedEdge> exporter = new DOTExporter<>();
                    Writer writer = new FileWriter("scaleFreeGraph.dot");
                    exporter.exportGraph(scaleFreeGraph, writer);
                    writer.close();
                }

                System.out.println("Scale-free:");
//                Iterator<String> iter2 = new DepthFirstIterator<>(scaleFreeGraph);
//                while (iter2.hasNext()) {
//                    String vertex = iter2.next();
//                    System.out.println(vertex + " is connected to: " + scaleFreeGraph.edgesOf(vertex).toString());
//                }

                Integer[][] scaleFreeAdjacency = simulationEngine1.generateAdjacencyMatrixFromGraph(scaleFreeGraph, numberOfAgents);

//                Integer[][] scaleFreeAdjacency = {{null, 1, 1, 1, 1, null, null, 1},
//                                    {1, null, null, null, null, null, null, null},
//                                    {1, null, null, null, 1, null, null, null},
//                                    {1, null, null, null, null, 1, null, 1},
//                                    {1, null, 1, null, null, 1, null, null},
//                                    {null, null, null, 1, 1, null, 1, null},
//                                    {null, null, null, null, null, 1, null, null},
//                                    {1, null, null, 1, null, null, null, null}};


//                Integer[][] scaleFreeAdjacency = {{null, 1, 1, null, 1, 1, null, null, null, null, null, null, null, null, null, 1, null, null, null, null},
//                    {1, null, null, 1, null, null, null, null, null, null, null, null, null, null, null, 1, null, null, null, null},
//                    {1, null, null, null, null, null, null, null, null, null, null, 1, 1, null, 1, null, null, null, 1, null},
//                    {null, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null},
//                    {1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null},
//                    {1, null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null, 1, null, null, null},
//                    {null, null, null, null, null, 1, null, 1, 1, null, null, null, null, 1, null, null, null, 1, null, 1},
//                    {null, null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, null, null, null},
//                    {null, null, null, null, null, null, 1, null, null, 1, 1, null, null, null, null, null, null, null, null, null},
//                    {null, null, null, null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, null},
//                    {null, null, null, null, null, null, null, null, 1, null, null, null, 1, null, null, null, null, null, null, null},
//                    {null, null, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null},
//                    {null, null, 1, null, null, null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null},
//                    {null, null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, null, null, null},
//                    {null, null, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null},
//                    {1, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null},
//                    {null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, 1},
//                    {null, null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, null, 1, null},
//                    {null, null, 1, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 1, null, null},
//                    {null, null, null, null, null, null, 1, null, null, null, null, null, null, null, null, null, 1, null, null, null}};

//                System.out.println("Agent social network adjacency matrix: ");
//                System.out.print("{");
//                for (int i = 0; i < scaleFreeAdjacency.length; i++) {
//                    System.out.print("{");
//                    for (int j = 0; j < scaleFreeAdjacency[i].length; j++) {
//                        if (scaleFreeAdjacency[i][j] == null) {
//                            System.out.print("null, ");
//                        } else {
//                            System.out.print(scaleFreeAdjacency[i][j] + ", ");
//                        }
//                    }
//                    System.out.println("}");
//                }
//                System.out.print("}");
//                System.out.println();

                agentControllers.clear();
//                currentTime = System.currentTimeMillis();
//                endTime = currentTime + duration;
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController1, agentController2;
                    try {
                        if (i == 0) {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, endTime, scaleFreeGraph, scaleFreeAdjacency, logFileNameMaster1, resultFileName1, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, numberOfRounds, scaleFreeGraph, scaleFreeAdjacency, logFileNameMaster2, resultFileName2, agentType2});
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

//                while (currentTime < endTime + 1500) {
//                    currentTime = System.currentTimeMillis();
//                }
//                for( AgentController agentController : agentControllers) {
//                    agentController.kill();
//                }
//                containerController.kill();
            }
        }
    }


    public static void randomSim() throws StaleProxyException {

        int numberOfAgents = 8;
        int numberOfRounds = 2000;
//        long duration = 600000;
//        long currentTime, endTime;
        SimulationEngine simulationEngine1, simulationEngine2;
        Set<AgentController> agentControllers = new HashSet<>();

        String agentType1 = "Random-NoCas-RL-A";
        String agentType2 = "Random-NoCas-A";

//        String resultFileName1 = "logs/results/" + agentType1 + "-" + new Date() + ".txt";
        String resultFileName2 = "logs/results/" + agentType2 + "-" + new Date() + ".txt";

        for (int degree = 4; degree <= 4; degree+=2) {
//            logResults(resultFileName1, "");
//            logResults(resultFileName1, "degree = " + degree);
//            logResults(resultFileName1, "");
//            logResults(resultFileName2, "");
//            logResults(resultFileName2, "degree = " + degree);
//            logResults(resultFileName2, "");
            simulationEngine1 = new SimulationEngine( 8, agentType1);
            simulationEngine2 = new SimulationEngine( 8, agentType2);
            for (int exp = 1; exp <= 1; exp++) {
//                String logFileNameMaster1 = "logs/" + "Master-" + agentType1 + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameMaster2 = "logs/" + "Master-" + agentType2 + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";
//                String logFileNameAll1 = "logs/" + "All-" + agentType1  + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileNameAll2 = "logs/" + "All-" + agentType2  + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

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
//              Integer[][] randomAdjacency = {{null, 1, 1, null, null, null, 1, 1},
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
//                currentTime = System.currentTimeMillis();
//                endTime = currentTime + duration;
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController1, agentController2;
                    try {
                        if (i == 0) {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.RLMasterAgent", new Object[]{numberOfAgents, endTime, null, randomAdjacency, logFileNameMaster1, resultFileName1, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, numberOfRounds, randomAdjacency, logFileNameMaster2, resultFileName2, agentType2});
                            agentController2.start();
                        } else {
//                            agentController1 = containerController.createNewAgent(agentType1 + i, "agents.RLNeighborAdaptiveAgent", new Object[]{numberOfAgents, i, endTime, randomAdjacency[i - 1], logFileNameAll1, simulationEngine1, true, agentType1});
//                            agentController1.start();
                            agentController2 = containerController.createNewAgent(agentType2 + i, "agents.RLNeighborAdaptiveAgent", new Object[]{numberOfAgents, i, numberOfRounds, randomAdjacency[i - 1], logFileNameAll2, simulationEngine2, agentType2});
                            agentController2.start();
                        }
//                        agentControllers.add( agentController1);
                        agentControllers.add( agentController2);
                    } catch (StaleProxyException e) {
                        e.printStackTrace();
                    }
                }

//                while (currentTime < endTime + 1500) {
//                    currentTime = System.currentTimeMillis();
//                }
//                for( AgentController agentController : agentControllers) {
//                    agentController.kill();
//                }
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
