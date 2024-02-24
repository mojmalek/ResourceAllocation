package experiments.thesis;

import jade.core.Agent;
import model.ResourceItem;
import model.ResourceType;
import model.SimEngineI;
import model.Task;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;

public class SimEngResourceRatio implements SimEngineI {

    long parameter;
    String agentType;
    int maxTaskNumPerAgent;
    int maxRequestQuantity;
    int resourceTypesNum;
    int maxResourceTypesNum;


    public SimEngResourceRatio(long parameter, String agentType, int maxTaskNumPerAgent, int maxRequestQuantity, int resourceTypesNum) {
        this.parameter = parameter;
        this.agentType = agentType;
        this.maxTaskNumPerAgent = maxTaskNumPerAgent;
        this.maxRequestQuantity = maxRequestQuantity;
        this.resourceTypesNum = resourceTypesNum;
    }


    public SortedSet<Task> findTasks(Agent myAgent, int episode) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
//        Set<String> requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10",
//                agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
//        Set<String> requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10");
//        Set<String> requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5");
        Set<String> requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4");

        int[] taskNums = new int[] {maxTaskNumPerAgent};
        if( requesters.contains(myAgent.getLocalName())) {
            taskNums = new int[] {maxTaskNumPerAgent};
        }
        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        long[] requiredQuantities;
        //ToDo: experiment with different utilities for different agents for evaluating the RL approach
        int myId = Integer.valueOf(myAgent.getLocalName().replace(agentType, ""));
        long[] utilities = new long[] {4, 9, 16, 25};
//        if( requesters.contains(myAgent.getLocalName())) {
//            utilities = new long[] {20};
//        }
        long quantity, utility;
        for (int j = 0; j < numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < resourceTypesNum; i++) {
                if( requesters.contains(myAgent.getLocalName())) {
                    if (resourceTypeValues[i] == ResourceType.A) {
                        requiredQuantities = new long[]{maxRequestQuantity/maxTaskNumPerAgent};
                    } else {
                        requiredQuantities = new long[]{maxRequestQuantity/maxTaskNumPerAgent};
                    }
                } else {
                    if (resourceTypeValues[i] == ResourceType.A) {
                        requiredQuantities = new long[]{maxRequestQuantity/maxTaskNumPerAgent};
                    } else {
                        requiredQuantities = new long[]{maxRequestQuantity/maxTaskNumPerAgent};
                    }
                }
                quantity = requiredQuantities[random.nextInt( requiredQuantities.length)];
                if (quantity > 0) {
                    requiredResources.put(resourceTypeValues[i], quantity);
                }
            }
            utility = utilities[j];
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


    public Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent, int episode) {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        long[] quantities = new long[]{7, 9, 11, 13, 15};
        long[] lifetimes = new long[] {1};
//        Set<String> offerers = Set.of(agentType + "21", agentType + "22", agentType + "23", agentType + "24", agentType + "25", agentType + "26", agentType + "27", agentType + "28", agentType + "29", agentType + "30",
//                agentType + "31", agentType + "32", agentType + "33", agentType + "34", agentType + "35", agentType + "36", agentType + "37", agentType + "38", agentType + "39", agentType + "40");
//        Set<String> offerers = Set.of(agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
//        Set<String> offerers = Set.of(agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10");
        Set<String> offerers = Set.of(agentType + "5", agentType + "6", agentType + "7", agentType + "8");
        long quantity;
        long lifetime;
        for (int i = 0; i < resourceTypesNum; i++) {
            if( offerers.contains(myAgent.getLocalName())) {
                if (parameter == 0) {
                    // in learning process
                    int index = episode % quantities.length;
                    quantity = quantities[index];
                } else {
                    quantity = parameter;
                }
            } else {
                quantity = 1;
            }
//          if (quantity > 0) {
            lifetime = lifetimes[random.nextInt( lifetimes.length)];
            SortedSet<ResourceItem> items = findResourceItems(resourceTypeValues[i], lifetime, quantity, myAgent.getLocalName());
            resources.put(resourceTypeValues[i], items);
//          }
        }

        return resources;
    }


    public SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, long lifeTime, long quantity, String agentName) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        for (long i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString() + '-' + agentName;
            resourceItems.add(new ResourceItem (id, resourceType, lifeTime));
        }
        return resourceItems;
    }


    public Integer[][] generateRandomAdjacencyMatrix(int numberOfAgents, double connectivity) {

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


    public Integer[][] generateRandomAdjacencyMatrix2(int numberOfAgents, int numberOfEdges) {

        Integer[][] adjacency = new Integer[numberOfAgents][numberOfAgents];
        Random random = new Random();
        int[] weights = new int[] {1};
        int weight;

        // all possible edges
        Set<String> edges = new HashSet<>();
        for (int i = 0; i < numberOfAgents; i++) {
            for (int j = i + 1; j < numberOfAgents; j++) {
                edges.add( String.valueOf(i) + j);
            }
        }

        // first connect each agent to its next
        for (int i = 0; i < numberOfAgents-1; i++) {
            weight = weights[random.nextInt( weights.length)];
            adjacency[i][i+1] = weight;
            adjacency[i+1][i] = weight;
            edges.remove(String.valueOf(i) + (i+1));
            numberOfEdges--;
        }

        // then randomly add more edges
        while (numberOfEdges > 0) {
            String[] edgeArray = edges.toArray(new String[edges.size()]);
            String edge = edgeArray[random.nextInt(edgeArray.length)];
            int i = Character.getNumericValue(edge.charAt(0));
            int j = Character.getNumericValue(edge.charAt(1));
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


//    public Integer[][] computeDistances(Integer[][] socialNetwork) {
//
//        Integer[][] distances = new Integer[socialNetwork.length][socialNetwork.length];
//
//        for (int i=0; i<socialNetwork.length; i++) {
//            for (int j=0; j<socialNetwork[i].length; j++) {
//                if (distances[i][j] == null) {
//                    if (socialNetwork[i][j] == null) {
//                        distances[i][j] = computeShortestDistance(socialNetwork, i, j);
//                    } else {
//                        // when there is an edge, we consider it as the selected path even if it is not the shortest path
//                        distances[i][j] = socialNetwork[i][j];
//                    }
//                }
//            }
//        }
//
//        return distances;
//    }


//    int computeShortestDistance( Integer[][] socialNetwork, int source, int destination) {
//
//        int shortestDistance = Integer.MAX_VALUE;
//        Map<Integer, Integer> distancesToSource = new HashMap<>();
//        Map<Integer, Integer> previousVisit = new HashMap<>();
//        Set<Integer> visited = new HashSet<>();
//        Set<Integer> unvisited = new HashSet<>();
//
//        for (int i=0; i<socialNetwork.length; i++) {
//            distancesToSource.put(i, Integer.MAX_VALUE);
//            unvisited.add( i);
//        }
//
//        distancesToSource.put(source, 0);
//        visited.add( source);
//        unvisited.remove(source);
//
//        while( unvisited.isEmpty() == false) {
//
//        }
//
//        return shortestDistance;
//    }

}
