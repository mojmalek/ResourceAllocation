package model;

import jade.core.Agent;

import java.util.*;

public class SimulationEngine2 {


    public SortedSet<Task> findTasks(Agent myAgent) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        int[] taskNums = new int[] {1, 2, 3, 4};
        int numOfTasks = taskNums[random.nextInt( taskNums.length)];
        long[] quantities;
        long[] utilities;
        Set<String> requesters = Set.of("8Agent1", "8Agent2", "8Agent3", "8Agent4");
        Set<String> bidders = Set.of("8Agent5", "8Agent6", "8Agent7", "8Agent8");
        if( bidders.contains(myAgent.getLocalName())) {
            utilities = new long[] {30, 50, 100};
        } else {
            utilities = new long[] {1, 5, 10, 20};
        }
        long quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i=0; i<resourceTypeValues.length; i++) {
                if( bidders.contains(myAgent.getLocalName()) && (resourceTypeValues[i] == ResourceType.A || resourceTypeValues[i] == ResourceType.B) ) {
                    quantities = new long[] {1, 2, 3, 4};
                } else {
                    quantities = new long[] {5, 6, 7, 8};
                }
                quantity = quantities[random.nextInt( quantities.length)];
//                if (quantity > 0) {
                    requiredResources.put(resourceTypeValues[i], quantity);
//                }
            }
//            utility = minUtil + random.nextLong(utilVariation);
            utility = utilities[random.nextInt( utilities.length)];
            String id = UUID.randomUUID().toString();
            Task newTask = new Task(id, utility, 20, requiredResources);
            tasks.add(newTask);
        }

        return tasks;
    }


    public Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent) {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        long[] quantities = new long[] {1, 2, 3};
        int[] lifetimes = new int[] {1, 2, 3, 4};
        Set<String> bidders = Set.of("8Agent5", "8Agent6", "8Agent7", "8Agent8");
        if( bidders.contains(myAgent.getLocalName())) {
            quantities = new long[] {7};
        }
        long quantity;
        int lifetime;
        for (int i = 0; i < resourceTypeValues.length; i++) {
            quantity = quantities[random.nextInt( quantities.length)];
//            if (quantity > 0) {
                lifetime = lifetimes[random.nextInt( lifetimes.length)];
                SortedSet<ResourceItem> items = findResourceItems(resourceTypeValues[i], lifetime, quantity);
                resources.put(resourceTypeValues[i], items);
//            }
        }

        return resources;
    }


    public SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, int lifeTime, long quantity) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        for (long i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString();
            resourceItems.add(new ResourceItem (id, resourceType, lifeTime));
        }
        return resourceItems;
    }

}
