package model;

import jade.core.AID;

import java.util.*;

public class Task {

    public String id;
    public long utility;
    public long deadline;
    public Map<ResourceType, Long> requiredResources;
//    HashMap<ResourceType, HashMap<Integer, TimeInterval>> requiredResources;
    public AID manager;
    public String agentType;

    public Task(String id, long utility, long deadline, Map<ResourceType, Long> requiredResources) {
        this.id = id;
        this.utility = utility;
        this.deadline = deadline;
        this.requiredResources = requiredResources;
    }

    public Task(String id, long utility, long deadline, Map<ResourceType, Long> requiredResources, AID manager) {
        this.id = id;
        this.utility = utility;
        this.deadline = deadline;
        this.requiredResources = requiredResources;
        this.manager = manager;
    }

    public double efficiency() {
        int count = 0;
        for (var resource: requiredResources.entrySet()) {
            count += resource.getValue();
        }
        return (double) utility / count;
    }


    @Override
    public String toString() {
        return "Task{" +
                "id='" + id + '\'' +
                ", utility=" + utility +
                ", requiredResources=" + requiredResources +
                ", manager=" + manager.getLocalName() +
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
            Integer managerId1 = Integer.valueOf(task1.manager.getLocalName().replace(task1.agentType, ""));
            Integer managerId2 = Integer.valueOf(task2.manager.getLocalName().replace(task1.agentType, ""));
            String id1 = task1.id;
            String id2 = task2.id;

            if (efficiency1.equals(efficiency2)) {
                if (managerId1.equals(managerId2)) {
                    return id2.compareTo(id1);
                }
                return managerId2.compareTo(managerId1);
            }
            return efficiency2.compareTo(efficiency1);
        }
    }

}
