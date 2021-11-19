package model;

import java.util.Date;
import java.util.HashMap;

public class Task {


    HashMap<ResourceType,String> requiredResources;

    public Task(HashMap<ResourceType, String> requiredResources) {
        this.requiredResources = requiredResources;
    }


    int util (Date currentTime) {

        int util = 1000;

        return util;
    }

    @Override
    public String toString() {
        return "Task with requiredResources=" + requiredResources;
    }



}
