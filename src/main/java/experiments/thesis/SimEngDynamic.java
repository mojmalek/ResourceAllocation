package experiments.thesis;

import jade.core.Agent;
import model.ResourceItem;
import model.ResourceType;
import model.SimEngineI;
import model.Task;

import java.util.*;

public class SimEngDynamic implements SimEngineI {

    long parameter;
    String agentType;
    int maxTaskNumPerAgent;
    int maxRequestQuantity;
    int resourceTypesNum;
    int maxResourceTypesNum;


    public SimEngDynamic(long parameter, String agentType, int maxTaskNumPerAgent, int maxRequestQuantity, int resourceTypesNum) {
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

        int[] taskNums = new int[] {1, 2, maxTaskNumPerAgent};
//        if( requesters.contains(myAgent.getLocalName())) {
//            taskNums = new int[] {1, 2, maxTaskNumPerAgent};
//        }
        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        long[] requiredQuantities;
        //ToDo: experiment with different utilities for different agents for evaluating the RL approach
        long[] utilities = new long[] {4, 9, 16, 25};
//        if( requesters.contains(myAgent.getLocalName())) {
//            utilities = new long[] {4, 9, 16, 25};
//        }
        long quantity, utility;
        for (int j = 0; j < numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < resourceTypesNum; i++) {
//                if( requesters.contains(myAgent.getLocalName())) {
                requiredQuantities = new long[]{1, 2, maxRequestQuantity/maxTaskNumPerAgent};
//                } else {
//                    requiredQuantities = new long[]{1, 2, maxRequestQuantity/maxTaskNumPerAgent};
//                }
                quantity = requiredQuantities[random.nextInt( requiredQuantities.length)];
                if (quantity > 0) {
                    requiredResources.put(resourceTypeValues[i], quantity);
                }
            }
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


    public Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent, int episode) {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        long[] quantities;
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
                    quantities = new long[]{7, 9, 11, 13, 15};
                    int index = episode % quantities.length;
                    quantity = quantities[index];
                } else {
                    quantity = parameter;
                }

            } else {
                quantities = new long[]{0, 1, 2};
                quantity = quantities[random.nextInt( quantities.length)];
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
