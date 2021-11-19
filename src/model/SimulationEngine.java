package model;

import java.util.Date;
import java.util.HashMap;

public class SimulationEngine {


    public Task findTask() {

        HashMap<ResourceType, String> requiredResources = new HashMap<>();
        requiredResources.put(ResourceType.A, "10 items for tomorrow");

        Task task = new Task( requiredResources);

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
