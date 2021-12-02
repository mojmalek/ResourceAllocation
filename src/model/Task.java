package model;

import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

public class Task {

    String id;
    int utility;
    HashMap<ResourceType, Integer> requiredResources;
//    HashMap<ResourceType, HashMap<Integer, TimeInterval>> requiredResources;

    public Task(String id, int utility, HashMap<ResourceType, Integer> requiredResources) {
        this.id = id;
        this.utility = utility;
        this.requiredResources = requiredResources;
    }

//    int util (Date currentTime) {
//
//        int util = 1000;
//
//        return util;
//    }


    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", utility=" + utility +
                ", requiredResources=" + requiredResources +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return id.equals(task.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
