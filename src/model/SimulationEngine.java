package model;

import jade.core.Agent;

import java.util.*;

public class SimulationEngine {


    public SortedSet<Task> findTasks(Agent myAgent) {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] resourceTypeValues = ResourceType.getValues();
        int numOfTasks = 3;
        int minQuantity = 1;
        int quantityVariation = 10;
        int minUtil = 1;
//        if( myAgent.getLocalName().equals("Agent1")) {
//            minUtil = 1;
//        } else {
//            minUtil = 1000;
//        }
        int utilVariation = 10;
        int quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Integer> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < resourceTypeValues.length; i++) {
                quantity = minQuantity + random.nextInt(quantityVariation);
                requiredResources.put(resourceTypeValues[i], quantity);
            }
            utility = minUtil + random.nextInt(utilVariation);
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
        int minQuantity = 1;
//        if( myAgent.getLocalName().equals("Agent1")) {
//            minQuantity = 1000;
//        } else {
//            minQuantity = 1;
//        }
        int quantityVariation = 5;
        int minLifetime = 3;
        int lifetimeVariantion = 3;
        int quantity, lifetime;
        for (int i = 0; i < resourceTypeValues.length; i++) {
            quantity = minQuantity + random.nextInt(quantityVariation);
            lifetime = minLifetime + random.nextInt(lifetimeVariantion);
            SortedSet<ResourceItem> items = findResourceItems( resourceTypeValues[i], lifetime, quantity);
            resources.put(resourceTypeValues[i], items);
        }

        return resources;
    }


    public SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, int lifeTime, int quantity) {

        SortedSet<ResourceItem> resourceItems = new TreeSet<>(new ResourceItem.resourceItemComparator());
        String id;
        for (int i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString();
            resourceItems.add(new ResourceItem (id, resourceType, lifeTime));
        }
        return resourceItems;
    }

}
