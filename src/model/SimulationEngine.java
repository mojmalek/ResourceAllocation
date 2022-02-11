package model;

import jade.core.Agent;

import java.util.*;

public class SimulationEngine {


    public SortedSet<Task> findTasks(Agent myAgent) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        int numOfTasks = 2;
        long minQuantity = 3;
        long quantityVariation = 3;
        long minUtil = 1;
//        if( myAgent.getLocalName().equals("Agent1")) {
//            minUtil = 1000;
//        } else {
//            minUtil = 1;
//        }
        long utilVariation = 100;
        long quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < resourceTypeValues.length; i++) {
//                if( myAgent.getLocalName().equals("Agent1")) {
//                    minQuantity = 1;
//                } else {
//                    minQuantity = 1000;
//                }
                quantity = minQuantity + random.nextLong(quantityVariation);
                requiredResources.put(resourceTypeValues[i], quantity);
            }
            utility = minUtil + random.nextLong(utilVariation);
            String id = UUID.randomUUID().toString();
            Task newTask = new Task(id, utility, requiredResources);
            tasks.add(newTask);
        }

        return tasks;
    }


    public Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent) {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        long minQuantity = 2;
//        if( myAgent.getLocalName().equals("Agent1")) {
//            minQuantity = 4;
//        } else {
//            minQuantity = 1;
//        }
        long quantityVariation = 6;
        int minLifetime = 2;
        int lifetimeVariation = 3;
        long quantity;
        int lifetime;
        for (int i = 0; i < resourceTypeValues.length; i++) {
            quantity = minQuantity + random.nextLong(quantityVariation);
            lifetime = minLifetime + random.nextInt(lifetimeVariation);
            SortedSet<ResourceItem> items = findResourceItems( resourceTypeValues[i], lifetime, quantity);
            resources.put(resourceTypeValues[i], items);
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
