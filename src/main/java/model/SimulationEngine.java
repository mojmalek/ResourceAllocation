package model;

import jade.core.Agent;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;

public class SimulationEngine {

    long parameter;
    String agentType;

    public SimulationEngine() {
    }

    public SimulationEngine(long parameter, String agentType) {
        this.parameter = parameter;
        this.agentType = agentType;
    }


    public SortedSet<Task> findTasks(Agent myAgent) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
//        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10",
//                agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
//        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10");
        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5");
//        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "3");

        int[] taskNums = new int[] {4};
        if( requesters.contains(myAgent.getLocalName())) {
            taskNums = new int[] {2};
        }
        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        long[] requiredQuantities;
        //ToDo: experiment with different utilities for different agents for evaluating the RL approach
//        long minUtil = 10;
//        long utilVariation = 5;
        long[] utilities = new long[] {20};
        if( requesters.contains(myAgent.getLocalName())) {
            utilities = new long[] {30};
//            minUtil = 20;
        }
        long quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i=0; i<resourceTypeValues.length; i++) {
                if( requesters.contains(myAgent.getLocalName())) {
                    if (resourceTypeValues[i] == ResourceType.A) {
                        requiredQuantities = new long[]{6};
                    } else {
                        requiredQuantities = new long[]{3};
                    }
                } else {
                    if (resourceTypeValues[i] == ResourceType.A) {
                        requiredQuantities = new long[]{4};
                    } else {
                        requiredQuantities = new long[]{2};
                    }
                }
                quantity = requiredQuantities[random.nextInt( requiredQuantities.length)];
                if (quantity > 0) {
                    requiredResources.put(resourceTypeValues[i], quantity);
                }
            }
//            utility = minUtil + random.nextLong(utilVariation);
            utility = utilities[random.nextInt( utilities.length)];
            String id = UUID.randomUUID().toString();
            if (!requiredResources.isEmpty()) {
                Task newTask = new Task(id, utility, 1, requiredResources, myAgent.getAID());
                newTask.agentType = agentType;
                tasks.add(newTask);
            } else {
//                System.out.println(" ");
            }
        }

        return tasks;
    }


    public Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent) {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        long[] quantities;
        long[] lifetimes = new long[] {1};
//        Set<String> offerers = Set.of(agentType + "21", agentType + "22", agentType + "23", agentType + "24", agentType + "25", agentType + "26", agentType + "27", agentType + "28", agentType + "29", agentType + "30",
//                agentType + "31", agentType + "32", agentType + "33", agentType + "34", agentType + "35", agentType + "36", agentType + "37", agentType + "38", agentType + "39", agentType + "40");
//        Set<String> offerers = Set.of(agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
        Set<String> offerers = Set.of(agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10");
//        Set<String> offerers = Set.of(agentType + "4");
        long quantity;
        long lifetime;
        for (int i = 0; i < resourceTypeValues.length; i++) {
            if( offerers.contains(myAgent.getLocalName())) {
                if (resourceTypeValues[i] == ResourceType.A) {
                    quantities = new long[]{5};
                } else {
                    quantities = new long[]{6};
                }
            } else {
                if (resourceTypeValues[i] == ResourceType.A) {
                    quantities = new long[]{4};
                } else {
                    quantities = new long[]{4};
                }
            }
            quantity = quantities[random.nextInt( quantities.length)];
//            if (quantity > 0) {
                lifetime = lifetimes[random.nextInt( lifetimes.length)];
                SortedSet<ResourceItem> items = findResourceItems(resourceTypeValues[i], lifetime, quantity, myAgent.getLocalName());
                resources.put(resourceTypeValues[i], items);
//            }
        }

        return resources;
    }


    private SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, long lifeTime, long quantity, String agentName) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        for (long i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString() + '-' + agentName;
            resourceItems.add(new ResourceItem (id, resourceType, lifeTime));
        }
        return resourceItems;
    }


    public static Integer[][] generateRandomAdjacencyMatrix(int numberOfAgents, double connectivity) {

        Integer[][] adjacency = new Integer[numberOfAgents][numberOfAgents];
        Random random = new Random();
        int[] weights = new int[] {1};
        int weight;

        // first connect each agent to its next
        for (int i = 0; i < numberOfAgents-1; i++) {
            weight = weights[random.nextInt( weights.length)];
            adjacency[i][i+1] = weight;
            adjacency[i+1][i] = weight;
        }

        // then consider connecting more agents based on the degree of connectivity
        for (int i = 0; i < numberOfAgents-1; i++) {
            for (int j = i+2; j < numberOfAgents; j++) {
                double r = random.nextDouble();
                if (r < connectivity) {
                    weight = weights[random.nextInt( weights.length)];
                    adjacency[i][j] = weight;
                    adjacency[j][i] = weight;
                }
            }
        }

        return adjacency;
    }


    public static Integer[][] generateRandomAdjacencyMatrix2(int numberOfAgents, int numberOfEdges) {

        Integer[][] adjacency = new Integer[numberOfAgents][numberOfAgents];
        Random random = new Random();
        int[] weights = new int[] {1};
        int weight;

        // all possible edges
        Set<String> edges = new HashSet<>();
        for (int i = 0; i < numberOfAgents; i++) {
            for (int j = i + 1; j < numberOfAgents; j++) {
                edges.add( i + "-" + j);
            }
        }

        // first connect each agent to its next
        for (int i = 0; i < numberOfAgents-1; i++) {
            weight = weights[random.nextInt( weights.length)];
            adjacency[i][i+1] = weight;
            adjacency[i+1][i] = weight;
            edges.remove(i + "-" + (i+1));
            numberOfEdges--;
        }

        // then randomly add more edges
        while (numberOfEdges > 0) {
            String[] edgeArray = edges.toArray(new String[edges.size()]);
            String edge = edgeArray[random.nextInt(edgeArray.length)];
            String[] nodes = edge.split("-");
            int i = Integer.parseInt(nodes[0]);
            int j = Integer.parseInt(nodes[1]);
            weight = weights[random.nextInt( weights.length)];
            adjacency[i][j] = weight;
            adjacency[j][i] = weight;
            edges.remove(edge);
            numberOfEdges--;
        }

        return adjacency;
    }


    public static Integer[][] generateAdjacencyMatrixFromGraph(Graph<String, DefaultWeightedEdge> graph, int numberOfAgents) {

        Integer[][] adjacency = new Integer[numberOfAgents][numberOfAgents];

        for (int i = 1; i <= numberOfAgents; i++) {
            for (int j = i + 1; j <= numberOfAgents; j++) {
                DefaultWeightedEdge edge = graph.getEdge(""+i, ""+j);
                if (edge != null) {
//                    System.out.println(graph.getEdgeWeight(edge));
                    adjacency[i-1][j-1] = (int) graph.getEdgeWeight(edge);
                    adjacency[j-1][i-1] = (int) graph.getEdgeWeight(edge);
                }
            }
        }

        return adjacency;
    }


    public static Graph<String, DefaultWeightedEdge> generateGraphFromAdjacencyMatrix(Integer[][] adjacency, Graph<String, DefaultWeightedEdge> graph, int numberOfAgents) {

        for (int i = 1; i <= numberOfAgents; i++) {
            graph.addVertex("" + i);
        }
        for (int i = 1; i <= numberOfAgents; i++) {
            for (int j = i + 1; j <= numberOfAgents; j++) {
                if (adjacency[i-1][j-1] != null) {
                    graph.addEdge("" + i, "" + j);
                }
            }
        }

        return graph;
    }

}
