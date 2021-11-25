package model;

import java.util.*;

public class SimulationEngine {


    public Task findTask() {

        HashMap<ResourceType, HashMap<Integer, TimeInterval>> requiredResources = new HashMap<>();

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

            HashMap<Integer, TimeInterval> quantityTimeInterval = new HashMap<>();
            quantityTimeInterval.put (quantity, new TimeInterval( new Date(), new Date()));

            ResourceType selectedResourceType = (ResourceType) it.next();

            requiredResources.put (selectedResourceType, quantityTimeInterval);

            resourceTypes.remove(selectedResourceType);
            it = resourceTypes.iterator();
        }



        low = 1;
        high = 100;
        int utility = random.nextInt(high) + low;

        Task task = new Task( requiredResources, utility);

        return task;
    }


    public ResourceItem findResourceItem() {

//        Params:
//        year – the year minus 1900.
//        month – the month between 0-11.
//        date – the day of the month between 1-31.
//        hrs – the hours between 0-23.
//        min – the minutes between 0-59.
        Date expiryDate = new Date(122, 2, 6, 19, 33);

        ResourceItem resourceItem = new ResourceItem( ResourceType.A, expiryDate);

        return resourceItem;
    }


}
