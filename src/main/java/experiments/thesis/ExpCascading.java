package experiments.thesis;

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
import org.jgrapht.nio.GraphExporter;
import org.jgrapht.nio.GraphImporter;
import org.jgrapht.nio.dot.DOTExporter;
import org.jgrapht.nio.dot.DOTImporter;
import org.jgrapht.util.SupplierUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Supplier;


public class ExpCascading {


    public static void main(String[] args) {
        try {
            smallWorldSim();
            scaleFreeSim();
            randomSim();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void smallWorldSim() throws StaleProxyException, IOException {

        boolean loadGraph = true;
        int numberOfAgents = 10;
        int numberOfEpisodes = 100;
        int packageSize = 20;
        int degree = 4;

        SimEngCascading simEngCascading;
        List<AgentController> agentControllers = new ArrayList<>();

        String agentType = "SmallWorld-A";

        String resultFileCen = "results/" + agentType + "-" + new Date() + "-CEN.txt";
        String resultFileDec = "results/" + agentType + "-" + new Date() + "-DEC.txt";

        for (int exp = 1; exp <= 20; exp++) {

            String trainedModelPath = "trained_models/cascading" + numberOfAgents + "smallWorld" + exp;

            logResults(resultFileCen, "");
            logResults(resultFileDec, "");

            Supplier<String> vSupplier = new Supplier<String>() {
                private int id = 1;
                @Override
                public String get() {
                    return "" + id++;
                }
            };

            Graph<String, DefaultWeightedEdge> smallWorldGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
            String graphFileName = "graphs/" + numberOfAgents + "smallWorld" + "-d" + degree + "-" + exp;
            if (loadGraph) {
                GraphImporter<String, DefaultWeightedEdge> importer = new DOTImporter<>();
                Reader reader = new FileReader(graphFileName);
                importer.importGraph(smallWorldGraph, reader);
                reader.close();
            } else {
                WattsStrogatzGraphGenerator<String, DefaultWeightedEdge> smallWorldGraphGenerator = new WattsStrogatzGraphGenerator<>(numberOfAgents, degree, 0.05);
                smallWorldGraphGenerator.generateGraph(smallWorldGraph);

                GraphExporter<String, DefaultWeightedEdge> exporter = new DOTExporter<>();
                Writer writer = new FileWriter(graphFileName);
                exporter.exportGraph(smallWorldGraph, writer);
                writer.close();
            }

            Integer[][] smallWorldAdjacency = SimulationEngine.generateAdjacencyMatrixFromGraph(smallWorldGraph, numberOfAgents);

            for (long param = 4; param <= 16; param += 2) {

                simEngCascading = new SimEngCascading( param, agentType, 4, 20, 1);
                simEngCascading.maxResourceTypesNum = 1;

                String logFileMaster = "logs/" + "Master-" + agentType + "-exp" + exp + "-param=" + param + "-" + new Date() + ".txt";
                String logFileAll = "logs/" + "All-" + agentType + "-exp" + exp + "-param=" + param + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

                agentControllers.clear();
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController;
                    try {
                        if (i == 0) {
                            agentController = containerController.createNewAgent(agentType + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, numberOfEpisodes, smallWorldGraph, smallWorldAdjacency, logFileMaster, resultFileCen, resultFileDec, agentType, simEngCascading.maxTaskNumPerAgent, simEngCascading.resourceTypesNum, simEngCascading.maxResourceTypesNum, trainedModelPath, packageSize});
                            agentController.start();
                        } else {
                            agentController = containerController.createNewAgent(agentType + i, "agents.DeepRLSocialAdaptiveAgent", new Object[]{numberOfAgents, numberOfEpisodes, smallWorldGraph, smallWorldAdjacency[i - 1], logFileAll, simEngCascading, agentType, simEngCascading.maxRequestQuantity, trainedModelPath, packageSize});
                            agentController.start();
                        }
                        agentControllers.add( agentController);
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


    public static void scaleFreeSim() throws StaleProxyException, IOException {

        boolean loadGraph = true;
        int numberOfAgents = 10;
        int numberOfEpisodes = 100;
        int packageSize = 20;

        SimEngCascading simEngCascading;
        List<AgentController> agentControllers = new ArrayList<>();

        String agentType = "ScaleFree-A";

        String resultFileCen = "results/" + agentType + "-" + new Date() + "-CEN.txt";
        String resultFileDec = "results/" + agentType + "-" + new Date() + "-DEC.txt";

        for (int exp = 1; exp <= 20; exp++) {

            String trainedModelPath = "trained_models/cascading" + numberOfAgents + "scaleFree" + exp;

            logResults(resultFileCen, "");
            logResults(resultFileDec, "");

            Supplier<String> vSupplier = new Supplier<String>() {
                private int id = 1;
                @Override
                public String get() {
                    return "" + id++;
                }
            };

            Graph<String, DefaultWeightedEdge> scaleFreeGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
            String graphFileName = "graphs/" + numberOfAgents + "scaleFree" + exp;
            if (loadGraph) {
                GraphImporter<String, DefaultWeightedEdge> importer = new DOTImporter<>();
                Reader reader = new FileReader(graphFileName);
                importer.importGraph(scaleFreeGraph, reader);
                reader.close();
            } else {
                ScaleFreeGraphGenerator<String, DefaultWeightedEdge> scaleFreeGraphGenerator = new ScaleFreeGraphGenerator<>(numberOfAgents);
                scaleFreeGraphGenerator.generateGraph(scaleFreeGraph);

                GraphExporter<String, DefaultWeightedEdge> exporter = new DOTExporter<>();
                Writer writer = new FileWriter(graphFileName);
                exporter.exportGraph(scaleFreeGraph, writer);
                writer.close();
            }

            Integer[][] scaleFreeAdjacency = SimulationEngine.generateAdjacencyMatrixFromGraph(scaleFreeGraph, numberOfAgents);

            for (long param = 4; param <= 16; param += 2) {

                simEngCascading = new SimEngCascading( param, agentType, 4, 20, 1);
                simEngCascading.maxResourceTypesNum = 1;

                String logFileMaster = "logs/" + "Master-" + agentType + "-exp" + exp + "-param=" + param + "-" + new Date() + ".txt";
                String logFileAll = "logs/" + "All-" + agentType + "-exp" + exp + "-param=" + param + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

                agentControllers.clear();
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController;
                    try {
                        if (i == 0) {
                            agentController = containerController.createNewAgent(agentType + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, numberOfEpisodes, scaleFreeGraph, scaleFreeAdjacency, logFileMaster, resultFileCen, resultFileDec, agentType, simEngCascading.maxTaskNumPerAgent, simEngCascading.resourceTypesNum, simEngCascading.maxResourceTypesNum, trainedModelPath, packageSize});
                            agentController.start();
                        } else {
                            agentController = containerController.createNewAgent(agentType + i, "agents.DeepRLSocialAdaptiveAgent", new Object[]{numberOfAgents, numberOfEpisodes, scaleFreeGraph, scaleFreeAdjacency[i - 1], logFileAll, simEngCascading, agentType, simEngCascading.maxRequestQuantity, trainedModelPath, packageSize});
                            agentController.start();
                        }
                        agentControllers.add( agentController);
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


    public static void randomSim() throws StaleProxyException, IOException {

        boolean loadGraph = true;
        int numberOfAgents = 10;
        int numberOfEpisodes = 100;
        int packageSize = 20;
        int degree = 4;

        SimEngCascading simEngCascading;
        List<AgentController> agentControllers = new ArrayList<>();

        String agentType = "Random-A";

        String resultFileCen = "results/" + agentType + "-" + new Date() + "-CEN.txt";
        String resultFileDec = "results/" + agentType + "-" + new Date() + "-DEC.txt";

        for (int exp = 1; exp <= 20; exp++) {

            String trainedModelPath = "trained_models/cascading" + numberOfAgents + "random" + exp;

            logResults(resultFileCen, "");
            logResults(resultFileDec, "");

            Supplier<String> vSupplier = new Supplier<String>() {
                private int id = 1;
                @Override
                public String get() {
                    return "" + id++;
                }
            };

            Integer[][] randomAdjacency;
            Graph<String, DefaultWeightedEdge> randomGraph = new SimpleWeightedGraph<>(vSupplier, SupplierUtil.createDefaultWeightedEdgeSupplier());
            String graphFileName = "graphs/" + numberOfAgents + "random" + "-d" + degree + "-" + exp;
            if (loadGraph) {
                GraphImporter<String, DefaultWeightedEdge> importer = new DOTImporter<>();
                Reader reader = new FileReader(graphFileName);
                importer.importGraph(randomGraph, reader);
                reader.close();
                randomAdjacency = SimulationEngine.generateAdjacencyMatrixFromGraph(randomGraph, numberOfAgents);
            } else {
//                RandomRegularGraphGenerator<String, DefaultWeightedEdge> randomGraphGenerator = new RandomRegularGraphGenerator<>(numberOfAgents, degree);
//                randomGraphGenerator.generateGraph(randomGraph);
                int numberOfEdges = degree * numberOfAgents / 2;
                randomAdjacency = SimulationEngine.generateRandomAdjacencyMatrix2(numberOfAgents, numberOfEdges);
                randomGraph = SimulationEngine.generateGraphFromAdjacencyMatrix(randomAdjacency, randomGraph, numberOfAgents);
                GraphExporter<String, DefaultWeightedEdge> exporter = new DOTExporter<>();
                Writer writer = new FileWriter(graphFileName);
                exporter.exportGraph(randomGraph, writer);
                writer.close();
            }

            for (long param = 4; param <= 16; param += 2) {

                simEngCascading = new SimEngCascading( param, agentType, 4, 20, 1);
                simEngCascading.maxResourceTypesNum = 1;

                String logFileMaster = "logs/" + "Master-" + agentType + "-exp" + exp + "-param=" + param + "-" + new Date() + ".txt";
                String logFileAll = "logs/" + "All-" + agentType + "-exp" + exp + "-param=" + param + "-" + new Date() + ".txt";

                Runtime rt = Runtime.instance();
                Profile profile = new ProfileImpl();
                profile.setParameter(Profile.MAIN_HOST, "localhost");
//              profile.setParameter(Profile.GUI, "true");
                ContainerController containerController = rt.createMainContainer(profile);

                agentControllers.clear();
                for (int i = 0; i <= numberOfAgents; i++) {
                    AgentController agentController;
                    try {
                        if (i == 0) {
                            agentController = containerController.createNewAgent(agentType + i, "agents.DeepRLMasterAgent", new Object[]{numberOfAgents, numberOfEpisodes, randomGraph, randomAdjacency, logFileMaster, resultFileCen, resultFileDec, agentType, simEngCascading.maxTaskNumPerAgent, simEngCascading.resourceTypesNum, simEngCascading.maxResourceTypesNum, trainedModelPath, packageSize});
                            agentController.start();
                        } else {
                            agentController = containerController.createNewAgent(agentType + i, "agents.DeepRLSocialAdaptiveAgent", new Object[]{numberOfAgents, numberOfEpisodes, randomGraph, randomAdjacency[i - 1], logFileAll, simEngCascading, agentType, simEngCascading.maxRequestQuantity, trainedModelPath, packageSize});
                            agentController.start();
                        }
                        agentControllers.add( agentController);
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
        List<List<Double>> experiments = new ArrayList<>();
        List<Double> currentExperiment = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().isEmpty()) {
                if (!currentExperiment.isEmpty()) {
                    experiments.add(currentExperiment);
                    currentExperiment = new ArrayList<>();
                }
            } else {
                currentExperiment.add(Double.parseDouble(line.trim()));
            }
        }
        if (!currentExperiment.isEmpty()) {
            experiments.add(currentExperiment);
        }

        int maxLength = experiments.stream().mapToInt(List::size).max().orElse(0);
        double[] averages = new double[maxLength];
        int[] counts = new int[maxLength];

        for (List<Double> experiment : experiments) {
            for (int i = 0; i < experiment.size(); i++) {
                averages[i] += experiment.get(i);
                counts[i]++;
            }
        }

        for (int i = 0; i < maxLength; i++) {
            averages[i] /= counts[i];
        }

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.APPEND)) {
            writer.newLine();
            for (double avg : averages) {
                writer.write(String.format("%.2f", avg));
                writer.newLine();
            }
        }
    }
}
