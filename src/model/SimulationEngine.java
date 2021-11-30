package model;

import java.util.*;

public class SimulationEngine {


    public Task findTask() {

        HashMap<ResourceType, Integer> requiredResources = new HashMap<>();

        Random random = new Random();

        int low = 1;
        int high = ResourceType.getSize();
        int requiredResourceTypesSize = random.nextInt(high) + low;

        EnumSet<ResourceType> resourceTypes = EnumSet.allOf(ResourceType.class);

        low = 1;
        high = 1000;
        int quantity = 0;

        Iterator it = resourceTypes.iterator();

        for (int i=0; i<requiredResourceTypesSize; i++) {

            quantity = random.nextInt(high) + low;

//            HashMap<Integer, TimeInterval> quantityTimeInterval = new HashMap<>();
//            quantityTimeInterval.put (quantity, null);

            ResourceType selectedResourceType = (ResourceType) it.next();

            requiredResources.put (selectedResourceType, quantity);

            resourceTypes.remove(selectedResourceType);
            it = resourceTypes.iterator();
        }

        low = 1;
        high = 100;
        int utility = random.nextInt(high) + low;

        Task task = new Task( requiredResources, utility);

        return task;
    }


    public ArrayList<ResourceItem> findResourceItems( ResourceType resourceType, int lifeTime, int quantity) {

        ArrayList<ResourceItem> resourceItems = new ArrayList<>();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, lifeTime);
        Date expiryDate = calendar.getTime();

        for (int i=0; i<quantity; i++) {
            resourceItems.add(new ResourceItem( resourceType, expiryDate));
        }

        return resourceItems;
    }


}
