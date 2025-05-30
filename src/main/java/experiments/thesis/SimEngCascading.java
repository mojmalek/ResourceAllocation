package experiments.thesis;

import jade.core.Agent;
import model.ResourceItem;
import model.ResourceType;
import model.SimEngineI;
import model.Task;

import java.util.*;

public class SimEngCascading implements SimEngineI {

    long parameter;
    String agentType;
    int maxTaskNumPerAgent;
    int maxRequestQuantity;
    int resourceTypesNum;
    int maxResourceTypesNum;


    public SimEngCascading(long parameter, String agentType, int maxTaskNumPerAgent, int maxRequestQuantity, int resourceTypesNum) {
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
//        Set<String> requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5", agentType + "6", agentType + "7", agentType + "8");
//        Set<String> requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4", agentType + "5");
//        Set<String> requesters = Set.of(agentType + "1", agentType + "2", agentType + "3", agentType + "4");
        Set<String> requesters = Set.of(agentType + "1", agentType + "3");

        int[] taskNums = new int[] {1};
        if( requesters.contains(myAgent.getLocalName())) {
            taskNums = new int[] {4};
        }

        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        //ToDo: experiment with different utilities for different agents for evaluating the RL approach
        int myId = Integer.valueOf(myAgent.getLocalName().replace(agentType, ""));
        long[] utilities = new long[] {10};
        if( requesters.contains(myAgent.getLocalName())) {
            utilities = new long[] {10, 20, 30, 40};
        }
        long quantity, utility;
        for (int j = 0; j < numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < resourceTypesNum; i++) {
                quantity = 4;
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
//        Set<String> offerers = Set.of(agentType + "21", agentType + "22", agentType + "23", agentType + "24", agentType + "25", agentType + "26", agentType + "27", agentType + "28", agentType + "29", agentType + "30",
//                agentType + "31", agentType + "32", agentType + "33", agentType + "34", agentType + "35", agentType + "36", agentType + "37", agentType + "38", agentType + "39", agentType + "40");
//        Set<String> offerers = Set.of(agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16", agentType + "17", agentType + "18", agentType + "19", agentType + "20");
//        Set<String> offerers = Set.of(agentType + "9", agentType + "10", agentType + "11", agentType + "12", agentType + "13", agentType + "14", agentType + "15", agentType + "16");
//        Set<String> offerers = Set.of(agentType + "6", agentType + "7", agentType + "8", agentType + "9", agentType + "10");
//        Set<String> offerers = Set.of(agentType + "5", agentType + "6", agentType + "7", agentType + "8");
        Set<String> offerers = Set.of(agentType + "6", agentType + "8");

        long[] lifetimes = new long[] {1};
        long quantity;
        long lifetime;
        for (int i = 0; i < resourceTypesNum; i++) {
            if( offerers.contains(myAgent.getLocalName())) {
                quantity = parameter;
            } else {
                quantity = 2;
            }
            lifetime = lifetimes[random.nextInt( lifetimes.length)];
            SortedSet<ResourceItem> items = findResourceItems(resourceTypeValues[i], lifetime, quantity, myAgent);
            resources.put(resourceTypeValues[i], items);
        }

        return resources;
    }


    private SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, long lifeTime, long quantity, Agent myAgent) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        for (long i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString() + '-' + myAgent.getLocalName();
            resourceItems.add(new ResourceItem (id, resourceType, lifeTime, myAgent.getAID()));
        }
        return resourceItems;
    }

}
