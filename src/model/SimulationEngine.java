package model;

import java.util.*;

public class SimulationEngine {


    public SortedSet<Task> findTasks() {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] values = ResourceType.getValues();
        int numOfTasks = 2;
        int minQuantity = 5;
        int quantityVariation = 6;
        int minUtil = 50;
        int utilVariation = 51;
        int quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Integer> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i++) {
                quantity = minQuantity + random.nextInt(quantityVariation);
                requiredResources.put(values[i], quantity);
            }
            utility = minUtil + random.nextInt(utilVariation);
            String id = UUID.randomUUID().toString();
            Task newTask = new Task(id, utility, requiredResources);
            tasks.add(newTask);
        }

        return tasks;
    }


    public Map<ResourceType, SortedSet<ResourceItem>> findResources () {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();
        Random random = new Random();
        ResourceType[] values = ResourceType.getValues();
        int minQuantity = 1;
        int quantityVariation = 10;
        int minLifetime = 1;
        int lifetimeVariantion = 5;
        int quantity, lifetime;
        for (int i = 0; i < values.length; i++) {
            quantity = minQuantity + random.nextInt(quantityVariation);
            lifetime = minLifetime + random.nextInt(lifetimeVariantion);
            SortedSet<ResourceItem> items = findResourceItems( values[i], lifetime, quantity);
            resources.put(values[i], items);
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
