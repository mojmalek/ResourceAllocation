package experiments.thesis;

import jade.core.Agent;
import model.ResourceItem;
import model.ResourceType;
import model.SimEngineI;
import model.Task;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;

public class SimEngTask implements SimEngineI {

    long parameter;
    String agentType;
    int maxTaskNumPerAgent;
    int maxRequestQuantity;
    int resourceTypesNum;
    int maxResourceTypesNum;


    public SimEngTask(long parameter, String agentType, int maxTaskNumPerAgent, int maxRequestQuantity, int resourceTypesNum) {
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
        Set<String> requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4");

        int[] taskNums = new int[] {1, 2, 3, 4};
        int numOfTasks;
        if (parameter == 0) {
            // in learning process
            int index = episode % taskNums.length;
            numOfTasks = taskNums[index];
        } else {
            numOfTasks = (int) parameter;
        }

        //TODO: experiment with different utilities for different agents for evaluating the RL approach
        int myId = Integer.valueOf(myAgent.getLocalName().replace(agentType, ""));
        long[] utilities = new long[] {5};
        if( requesters.contains(myAgent.getLocalName())) {
            utilities = new long[] {5};
        }
        long utility;
        for (int j = 0; j < numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < resourceTypesNum; i++) {
                requiredResources.put(resourceTypeValues[i], (long) (maxRequestQuantity/maxTaskNumPerAgent));
            }
            utility = utilities[random.nextInt( utilities.length)];
            String id = UUID.randomUUID().toString();
            Task newTask = new Task(id, utility, 1, requiredResources, myAgent.getAID());
            newTask.agentType = agentType;
            tasks.add(newTask);
        }

        return tasks;
    }


    public Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent, int episode) {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        Set<String> offerers = Set.of(agentType + "5", agentType + "6", agentType + "7", agentType + "8");

        long[] lifetimes = new long[] {1};
        long quantity;
        long lifetime;
        for (int i = 0; i < resourceTypesNum; i++) {
            if( offerers.contains(myAgent.getLocalName())) {
                quantity = 11;
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
