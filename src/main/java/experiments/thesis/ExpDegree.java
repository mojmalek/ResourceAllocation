package experiments.thesis;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import org.jgrapht.Graph;
import org.jgrapht.generate.CompleteGraphGenerator;
import org.jgrapht.generate.RandomRegularGraphGenerator;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Supplier;


public class ExpDegree {


    public static void main(String[] args) {
        try {
//            smallWorldSim();
            randomSim();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void smallWorldSim() throws StaleProxyException {

        int numberOfAgents = 8;
        int numberOfRounds = 20000;
//        long duration = 600000;
//        long currentTime, endTime;
        SimEngDynamic simulationEngine1, simulationEngine2;
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
            simulationEngine1 = new SimEngDynamic( param, agentType1);
            simulationEngine2 = new SimEngDynamic( param, agentType2);
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


    public static void randomSim() throws StaleProxyException, IOException {

        boolean loadGraph = true;
        int numberOfAgents = 10;
        int numberOfEpisodes = 100;
        int packageSize = 10;

        SimEngDegree simEngDegree;
        List<AgentController> agentControllers = new ArrayList<>();

        String agentType = "Random-A";

        String resultFileCen = "results/" + agentType + "-" + new Date() + "-CEN.txt";
        String resultFileDec = "results/" + agentType + "-" + new Date() + "-DEC.txt";

        for (int degree = 2; degree <= 6; degree += 1) {

            logResults(resultFileCen, "");
            logResults(resultFileDec, "");

            simEngDegree = new SimEngDegree( numberOfAgents, agentType, 2, 10, 4);
            simEngDegree.maxResourceTypesNum = 4;

            for (int exp = 1; exp <= 10; exp++) {

                String trainedModelPath = "trained_models/degree" + numberOfAgents + "random" + "-d" + degree + "-" + exp;

                Supplier<String> vSupplier = new Supplier<String>() {
                    private int id = 1;
                    @Override
                    public String get() {
                        return "" + id++;
                    }
                };

                int numberOfEdges = degree * numberOfAgents / 2;
                Integer[][] randomAdjacency = simEngDegree.generateRandomAdjacencyMatrix2(numberOfAgents, numberOfEdges);
                Graph<String, DefaultWeightedEdge> randomGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
                String graphFileName = "graphs/" + numberOfAgents + "random" + "-d" + degree + "-" + exp;
                if (loadGraph) {
                    GraphImporter<String, DefaultWeightedEdge> importer = new DOTImporter<>();
                    Reader reader = new FileReader(graphFileName);
                    importer.importGraph(randomGraph, reader);
                    reader.close();
                } else {
//                    RandomRegularGraphGenerator<String, DefaultWeightedEdge> randomGraphGenerator = new RandomRegularGraphGenerator<>(numberOfAgents, degree);
//                    randomGraphGenerator.generateGraph(randomGraph);
                    randomGraph = SimEngDegree.generateGraphFromAdjacencyMatrix(randomAdjacency, randomGraph, numberOfAgents);

                    GraphExporter<String, DefaultWeightedEdge> exporter = new DOTExporter<>();
                    Writer writer = new FileWriter(graphFileName);
                    exporter.exportGraph(randomGraph, writer);
                    writer.close();
                }

//                Integer[][] randomAdjacency = SimEngDegree.generateAdjacencyMatrixFromGraph(randomGraph, numberOfAgents);

                String logFileMaster1 = "logs/" + "Master-" + agentType + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";
                String logFileAll1 = "logs/" + "All-" + agentType + "-degree=" + degree + "-exp" + exp + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

                agentControllers.clear();
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController1;
                    try {
                        if (i == 0) {
                            agentController1 = containerController.createNewAgent(agentType + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, numberOfEpisodes, randomGraph, randomAdjacency, logFileMaster1, resultFileCen, resultFileDec, agentType, simEngDegree.maxTaskNumPerAgent, simEngDegree.resourceTypesNum, simEngDegree.maxResourceTypesNum, trainedModelPath, packageSize});
                            agentController1.start();
                        } else {
                            agentController1 = containerController.createNewAgent(agentType + i, "agents.DeepRLSocialAdaptiveAgent", new Object[]{numberOfAgents, i, numberOfEpisodes, randomAdjacency[i - 1], logFileAll1, simEngDegree, agentType, simEngDegree.maxRequestQuantity, trainedModelPath, packageSize});
                            agentController1.start();
                        }
                        agentControllers.add( agentController1);
                    } catch (StaleProxyException e) {
                        e.printStackTrace();
                    }
                }

                while (agentControllers.get(0).getState().getName() != "Suspended") {
                    // wait
                }
                for( AgentController agentController : agentControllers) {
                    agentController.kill();
                }
//                containerController.kill();
            }
        }

        calculateAverages(resultFileCen);
        calculateAverages(resultFileDec);
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


    public static void calculateAverages(String filePath) throws IOException {

        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);
        List<Double> current = new ArrayList<>();
        double sum;
        double avg;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                if (!current.isEmpty()) {
                    sum = 0;
                    for (int i = 0; i < current.size(); i++) {
                        sum += current.get(i);
                    }
                    avg = sum / current.size();
                    try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND)) {
                        writer.newLine();
                        writer.write(String.format("%.2f", avg));
                    }
                    current = new ArrayList<>();
                }
            } else {
                current.add(Double.parseDouble(line.trim()));
            }
        }
        if (!current.isEmpty()) {
            sum = 0;
            for (int i = 0; i < current.size(); i++) {
                sum += current.get(i);
            }
            avg = sum / current.size();
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND)) {
                writer.newLine();
                writer.write(String.format("%.2f", avg));
            }
        }
    }
}
