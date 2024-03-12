package experiments.thesis;

import jade.core.Agent;
import model.ResourceItem;
import model.ResourceType;
import model.SimEngineI;
import model.Task;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.*;

public class SimEngAgent implements SimEngineI {

    int numberOfAgents;
    String agentType;
    int maxTaskNumPerAgent;
    int maxRequestQuantity;
    int resourceTypesNum;
    int maxResourceTypesNum;


    public SimEngAgent(int numberOfAgents, String agentType, int maxTaskNumPerAgent, int maxRequestQuantity, int resourceTypesNum) {
        this.numberOfAgents = numberOfAgents;
        this.agentType = agentType;
        this.maxTaskNumPerAgent = maxTaskNumPerAgent;
        this.maxRequestQuantity = maxRequestQuantity;
        this.resourceTypesNum = resourceTypesNum;
    }


    public SortedSet<Task> findTasks(Agent myAgent, int episode) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();

        Set<String> requesters = Set.of(agentType + "1", agentType + "2");
        if (numberOfAgents == 8) {
            requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4");
        }
        if (numberOfAgents == 12) {
            requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6");
        }
        if (numberOfAgents == 16) {
            requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8");
        }
        if (numberOfAgents == 20) {
            requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10");
        }
        if (numberOfAgents == 24) {
            requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10", agentType + "11", agentType + "12");
        }
        if (numberOfAgents == 32) {
            requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10", agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16");
        }
        if (numberOfAgents == 40) {
            requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10", agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
        }

        int[] taskNums = new int[] {maxTaskNumPerAgent};
        if( requesters.contains(myAgent.getLocalName())) {
            taskNums = new int[] {maxTaskNumPerAgent};
        }
        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        //ToDo: experiment with different utilities for different agents for evaluating the RL approach
        int myId = Integer.valueOf(myAgent.getLocalName().replace(agentType, ""));
        long[] utilities = new long[] {9, 16};
//        if( requesters.contains(myAgent.getLocalName())) {
//            utilities = new long[] {20};
//        }
        long quantity, utility;
        for (int j = 0; j < numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < resourceTypesNum; i++) {
                quantity = 2;
                requiredResources.put(resourceTypeValues[i], quantity);
            }
            utility = utilities[j];
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

        Set<String> offerers = Set.of(agentType + "3", agentType + "4");
        if (numberOfAgents == 8) {
            offerers = Set.of(agentType + "5", agentType + "6", agentType + "7", agentType + "8");
        }
        if (numberOfAgents == 12) {
            offerers = Set.of(agentType + "7", agentType + "8", agentType + "9", agentType + "10", agentType + "11", agentType + "12");
        }
        if (numberOfAgents == 16) {
            offerers = Set.of(agentType + "9", agentType + "10", agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16");
        }
        if (numberOfAgents == 20) {
            offerers = Set.of(agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
        }
        if (numberOfAgents == 24) {
            offerers = Set.of(agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20", agentType + "21", agentType + "22", agentType + "23", agentType + "24");
        }
        if (numberOfAgents == 32) {
            offerers = Set.of(agentType + "17", agentType + "18", agentType + "19", agentType + "20", agentType + "21", agentType + "22", agentType + "23", agentType + "24", agentType + "25", agentType + "26", agentType + "27", agentType + "28", agentType + "29", agentType + "30", agentType + "31", agentType + "32");
        }
        if (numberOfAgents == 40) {
            offerers = Set.of(agentType + "21", agentType + "22", agentType + "23", agentType + "24", agentType + "25", agentType + "26", agentType + "27", agentType + "28", agentType + "29", agentType + "30", agentType + "31", agentType + "32", agentType + "33", agentType + "34", agentType + "35", agentType + "36", agentType + "37", agentType + "38", agentType + "39", agentType + "40");
        }

        long[] lifetimes = new long[] {1};
        long quantity, lifetime;
        for (int i = 0; i < resourceTypesNum; i++) {
            if( offerers.contains(myAgent.getLocalName())) {
                quantity = 5;
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


    private SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, long lifeTime, long quantity, String agentName) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        for (long i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString() + '-' + agentName;
            resourceItems.add(new ResourceItem (id, resourceType, lifeTime));
        }
        return resourceItems;
    }

}
