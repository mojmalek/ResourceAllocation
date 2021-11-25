package model;

import java.util.Date;
import java.util.HashMap;

public class Task {


    HashMap<ResourceType, HashMap<Integer, TimeInterval>> requiredResources;

    int utility;

    public Task(HashMap<ResourceType, HashMap<Integer, TimeInterval>> requiredResources, int utility) {
        this.requiredResources = requiredResources;
        this.utility = utility;
    }


//    int util (Date currentTime) {
//
//        int util = 1000;
//
//        return util;
//    }

    @Override
    public String toString() {
        return "Task with requiredResources=" + requiredResources + " and utility=" + utility;
    }



}
