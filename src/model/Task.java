package model;

import java.util.*;

public class Task {

    String id;
    int utility;
    Map<ResourceType, Integer> requiredResources;
//    HashMap<ResourceType, HashMap<Integer, TimeInterval>> requiredResources;

    public Task(String id, int utility, Map<ResourceType, Integer> requiredResources) {
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


    public static class taskComparator implements Comparator<Task> {
        public int compare(Task task1, Task task2)
        {
            Integer utility1 = task1.utility;
            Integer utility2 = task2.utility;
            String id1 = task1.id;
            String id2 = task2.id;

            if (utility1 == utility2) {
                return id2.compareTo(id1);
            } else {
                return utility2.compareTo(utility1);
            }
        }
    }

}
