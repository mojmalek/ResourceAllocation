package model;

import java.util.*;

public class SimulationEngine {


    public ArrayList<Task> findTasks() {

        ArrayList<Task> tasks = new ArrayList<>();

        for (int j=0; j<3; j++) {

            Map<ResourceType, Integer> requiredResources = new LinkedHashMap<>();

            Random random = new Random();

            int low = 1;
            int high = ResourceType.getSize();
            int requiredResourceTypesSize = random.nextInt(high) + low;

            EnumSet<ResourceType> resourceTypes = EnumSet.allOf(ResourceType.class);

            low = 1;
            high = 100;
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


    public ArrayList<ResourceItem> findResourceItems( ResourceType resourceType, int lifeTime, int quantity) {

        ArrayList<ResourceItem> resourceItems = new ArrayList<>();

//        Calendar calendar = Calendar.getInstance();
//        calendar.add(Calendar.SECOND, lifeTime);
//        Date expiryDate = calendar.getTime();

        String id;
        for (int i=0; i<quantity; i++) {
            id = UUID.randomUUID().toString();
            resourceItems.add(new ResourceItem (id, resourceType, lifeTime));
        }

        return resourceItems;
    }


}
