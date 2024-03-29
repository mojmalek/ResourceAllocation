package model;

import jade.core.Agent;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;

public class TimedSimulationEngine {

    long parameter;
    String agentType;

    public TimedSimulationEngine() {
    }

    public TimedSimulationEngine(long parameter, String agentType) {
        this.parameter = parameter;
        this.agentType = agentType;
    }

    long currentTime;

    public SortedSet<Task> findTasks(Agent myAgent) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
//        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "7", agentType + "8", agentType + "13", agentType + "14");
//        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10",
//                agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
//        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10");
        Set<String> requesters = Set.of( agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5");
//        Set<String> requesters = Set.of( agentType + "1", agentType + "2");
        int[] taskNums = new int[] {4};
        if( requesters.contains(myAgent.getLocalName())) {
            taskNums = new int[] {2};
        }
        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        long[] requiredQuantities;
//        long minUtil = 10;
//        long utilVariation = 5;
        long[] utilities = new long[] {20};
        if( requesters.contains(myAgent.getLocalName())) {
            utilities = new long[] {30};
//            minUtil = 20;
        }
        long quantity, utility;
        //all tasks for this agent will have the same deadline
        currentTime = System.currentTimeMillis();
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i=0; i<resourceTypeValues.length; i++) {
                if( requesters.contains(myAgent.getLocalName())) {
                    if (resourceTypeValues[i] == ResourceType.A) {
                        requiredQuantities = new long[]{5};
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
                Task newTask = new Task(id, utility, currentTime + 20000, requiredResources, myAgent.getAID());
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
        long[] lifetimes = new long[] {20000};
//        Set<String> offerers = Set.of(agentType + "17", agentType + "18", agentType + "23", agentType + "24", agentType + "27", agentType + "28");
//        Set<String> offerers = Set.of(agentType + "6", agentType + "7");
//        Set<String> offerers = Set.of(agentType + "21", agentType + "22", agentType + "23", agentType + "24", agentType + "25", agentType + "26", agentType + "27", agentType + "28", agentType + "29", agentType + "30",
//                agentType + "31", agentType + "32", agentType + "33", agentType + "34", agentType + "35", agentType + "36", agentType + "37", agentType + "38", agentType + "39", agentType + "40");
//        Set<String> offerers = Set.of(agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
        Set<String> offerers = Set.of(agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10");
//        Set<String> offerers = Set.of(agentType + "3", agentType + "4");
        long quantity;
        long lifetime;
        for (int i = 0; i < resourceTypeValues.length; i++) {
            if( offerers.contains(myAgent.getLocalName())) {
                if (resourceTypeValues[i] == ResourceType.A) {
                    quantities = new long[]{10};
                } else {
                    quantities = new long[]{6};
                }
            } else {
                if (resourceTypeValues[i] == ResourceType.A) {
                    quantities = new long[]{8};
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


    public SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, long lifeTime, long quantity, String agentName) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        //all resource items for this agent will have the same expiry time
        currentTime = System.currentTimeMillis();
        for (long i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString() + '-' + agentName;
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


    public Integer[][] generateRandomAdjacencyMatrix2(int numberOfAgents, int numberOfEdges) {

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
