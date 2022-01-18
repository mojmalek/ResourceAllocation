package model;

import java.util.*;

public class SimulationEngine {


    public SortedSet<Task> findTasks() {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] values = ResourceType.getValues();
        int numOfTasks = 2;
        int minQuantity = 5;
        int maxQuantity = 5;
        int minUtil = 50;
        int maxUtil = 100;
        int quantity, utility;
        for (int j=0; j<numOfTasks; j++) {
            Map<ResourceType, Integer> requiredResources = new LinkedHashMap<>();
            for (int i = 0; i < values.length; i++) {
                quantity = random.nextInt(maxQuantity) + minQuantity;
                requiredResources.put(values[i], quantity);
            }
            utility = random.nextInt(maxUtil) + minUtil;
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
        int maxQuantity = 10;
        int minLifetime = 1;
        int maxLifetime = 3;
        int quantity, lifetime;
        for (int i = 0; i < values.length; i++) {
            quantity = random.nextInt(maxQuantity) + minQuantity;
            lifetime = random.nextInt(maxLifetime) + minLifetime;
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
