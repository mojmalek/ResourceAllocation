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

    long currentTime;

    public SortedSet<Task> findTasks(Agent myAgent) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
//        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "7", agentType + "8", agentType + "13", agentType + "14");
        Set<String> requesters = Set.of( agentType + "1", agentType + "2");
        int[] taskNums = new int[] {1};
        if( requesters.contains(myAgent.getLocalName())) {
            taskNums = new int[] {4};
        }
        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        long[] quantities = new long[] {4};
//        long minUtil = 10;
//        long utilVariation = 5;
        long[] utilities = new long[] {10};
//        if( bidders.contains(myAgent.getLocalName())) {
//            minUtil = 20;
//        }
        long quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i=0; i<resourceTypeValues.length; i++) {
//                if( bidders.contains(myAgent.getLocalName()) && resourceTypeValues[i] == ResourceType.A ) {
//                    quantities = new long[] {4, 5};
//                } else {
//                    quantities = new long[] {0, 1, 2, 3, 4};
//                }
                quantity = quantities[random.nextInt( quantities.length)];
                if (quantity > 0) {
                    requiredResources.put(resourceTypeValues[i], quantity);
                }
            }
//            utility = minUtil + random.nextLong(utilVariation);
            utility = utilities[random.nextInt( utilities.length)];
            String id = UUID.randomUUID().toString();
            if (!requiredResources.isEmpty()) {
                currentTime = System.currentTimeMillis();
                Task newTask = new Task(id, utility, currentTime + 2000, requiredResources);
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
        long[] quantities = new long[] {2};
        long[] lifetimes = new long[] {10000};
//        Set<String> offerers = Set.of(agentType + "17", agentType + "18", agentType + "23", agentType + "24", agentType + "27", agentType + "28");
        Set<String> offerers = Set.of(agentType + "6", agentType + "7");
        if( offerers.contains(myAgent.getLocalName())) {
            quantities = new long[] {parameter};
        }
        long quantity;
        long lifetime;
        for (int i = 0; i < resourceTypeValues.length; i++) {
            quantity = quantities[random.nextInt( quantities.length)];
//            if (quantity > 0) {
                lifetime = lifetimes[random.nextInt( lifetimes.length)];
                SortedSet<ResourceItem> items = findResourceItems(resourceTypeValues[i], lifetime, quantity, myAgent.getLocalName());
                resources.put(resourceTypeValues[i], items);
//            }
        }

        return resources;
    }


    public SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, long lifeTime, long quantity, String agentName) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        for (long i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString() + '-' + agentName;
            currentTime = System.currentTimeMillis();
            resourceItems.add(new ResourceItem (id, resourceType, currentTime + lifeTime));
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


    public Integer[][] generateAdjacencyMatrixFromGraph (Graph<String, DefaultWeightedEdge > graph, int numberOfAgents) {

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
