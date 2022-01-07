package model;

import java.util.*;

public class SimulationEngine {


    public SortedSet<Task> findTasks() {

        SortedSet<Task> tasks = new TreeSet<>(new Task.taskComparator());

        for (int j=0; j<3; j++) {

            Map<ResourceType, Integer> requiredResources = new LinkedHashMap<>();

            Random random = new Random();

            int low = 1;
            int high = ResourceType.getSize();
            int requiredResourceTypesSize = random.nextInt(high) + low;

            EnumSet<ResourceType> resourceTypes = EnumSet.allOf(ResourceType.class);

            low = 1;
            high = 10;
            int quantity = 0;

            Iterator it = resourceTypes.iterator();

            for (int i = 0; i < requiredResourceTypesSize; i++) {

                quantity = random.nextInt(high) + low;

//            HashMap<Integer, TimeInterval> quantityTimeInterval = new HashMap<>();
//            quantityTimeInterval.put (quantity, null);

                ResourceType selectedResourceType = (ResourceType) it.next();

                requiredResources.put(selectedResourceType, quantity);

                resourceTypes.remove(selectedResourceType);
                it = resourceTypes.iterator();
            }

            low = 1;
            high = 100;
            int utility = random.nextInt(high) + low;

            String id = UUID.randomUUID().toString();

            Task newTask = new Task(id, utility, requiredResources);

            tasks.add(newTask);
        }

        return tasks;
    }


    public Map<ResourceType, SortedSet<ResourceItem>> findResources () {

        Map<ResourceType, SortedSet<ResourceItem>> resources = new LinkedHashMap<>();

        Random random = new Random();
        int low = 1;
        int high = ResourceType.getSize();
        int foundResourceTypesSize = random.nextInt(high) + low;

        EnumSet<ResourceType> resourceTypes = EnumSet.allOf(ResourceType.class);
        Iterator it = resourceTypes.iterator();

        int quantity;
        int lifetime;
        for (int i = 0; i < foundResourceTypesSize; i++) {
            low = 5;
            high = 15;
            quantity = random.nextInt(high) + low;
            low = 1;
            high = 5;
            lifetime = random.nextInt(high) + low;
            ResourceType selectedResourceType = (ResourceType) it.next();
            SortedSet<ResourceItem> items = findResourceItems( selectedResourceType, lifetime, quantity);
            resources.put(selectedResourceType, items);
            resourceTypes.remove(selectedResourceType);
            it = resourceTypes.iterator();
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
