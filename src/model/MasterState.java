package model;


import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

public class MasterState {

    public SortedSet<Task> toDoTasks;

    public ArrayList<Double> efficiencies;

    // Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources
    public Map<ResourceType, SortedSet<ResourceItem>> availableResources;

    public Map<ResourceType, Long> availableQuantities;

    public long currentAllocatedQuantity;


    public MasterState(SortedSet<Task> toDoTasks, ArrayList<Double> efficiencies, Map<ResourceType, SortedSet<ResourceItem>> availableResources, Map<ResourceType, Long> availableQuantities, long currentAllocatedQuantity) {
        this.toDoTasks = toDoTasks;
        this.efficiencies = efficiencies;
        this.availableResources = availableResources;
        this.availableQuantities = availableQuantities;
        this.currentAllocatedQuantity = currentAllocatedQuantity;
    }


    Double efficienciesSum () {
        Double sum = 0.0;
        for (double e : efficiencies) {
            sum += e;
        }
        return sum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MasterState that = (MasterState) o;
        return Objects.equals(currentAllocatedQuantity, that.currentAllocatedQuantity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentAllocatedQuantity);
    }
}