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
        if( myAgent.getLocalName().equals("Agent1")) {
            minUtil = 100;
        }
        if( myAgent.getLocalName().equals("Agent2")) {
            minUtil = 100;
        }
        if( myAgent.getLocalName().equals("Agent3")) {
            minUtil = 100;
        }
        if( myAgent.getLocalName().equals("Agent4")) {
            minUtil = 100;
        }
        long utilVariation = 50;
        long quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Long> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < resourceTypeValues.length; i++) {
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
        if( myAgent.getLocalName().equals("Agent5")) {
            minQuantity = 5;
        }
        if( myAgent.getLocalName().equals("Agent6")) {
            minQuantity = 5;
        }
        if( myAgent.getLocalName().equals("Agent7")) {
            minQuantity = 5;
        }
        if( myAgent.getLocalName().equals("Agent8")) {
            minQuantity = 5;
        }
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
