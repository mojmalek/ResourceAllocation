package model;

import java.util.*;

public class Task {

    String id;
    long utility;
    Map<ResourceType, Long> requiredResources;
//    HashMap<ResourceType, HashMap<Integer, TimeInterval>> requiredResources;

    public Task(String id, long utility, Map<ResourceType, Long> requiredResources) {
        this.id = id;
        this.utility = utility;
        this.requiredResources = requiredResources;
    }


    private double efficiency() {
        int count = 0;
        for (var resource: requiredResources.entrySet()) {
            count += resource.getValue();
        }
        return (double) utility / count;
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
            Double efficiency1 = task1.efficiency();
            Double efficiency2 = task2.efficiency();
//            Integer utility1 = task1.utility;
//            Integer utility2 = task2.utility;
            String id1 = task1.id;
            String id2 = task2.id;

            if (efficiency1.equals(efficiency2)) {
//                int check = id2.compareTo(id1);
                return id2.compareTo(id1);
            } else {
//                int check = efficiency2.compareTo(efficiency1);
                return efficiency2.compareTo(efficiency1);
            }
        }
    }

}
