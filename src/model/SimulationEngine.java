package model;

import java.util.*;

public class SimulationEngine {


    public SortedSet<Task> findTasks() {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());
        Random random = new Random();
        ResourceType[] values = ResourceType.getValues();

        int low, high, quantity, utility;
        for (int j=0; j<3; j++) {
            Map<ResourceType, Integer> requiredResources = new LinkedHashMap<>();
            low = 10;
            high = 10;
            for (int i = 0; i < values.length; i++) {
                quantity = random.nextInt(high) + low;
                requiredResources.put(values[i], quantity);
            }
            low = 1;
            high = 100;
            utility = random.nextInt(high) + low;
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

        int low, high, quantity, lifetime;
        for (int i = 0; i < values.length; i++) {
            low = 1;
            high = 1;
            quantity = random.nextInt(high) + low;
            low = 1;
            high = 5;
            lifetime = random.nextInt(high) + low;
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
