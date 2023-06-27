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


    public MasterState(SortedSet<Task> toDoTasks, ArrayList<Double> efficiencies, Map<ResourceType, SortedSet<ResourceItem>> availableResources, Map<ResourceType, Long> availableQuantities) {
        this.toDoTasks = toDoTasks;
        this.efficiencies = efficiencies;
        this.availableResources = availableResources;
        this.availableQuantities = availableQuantities;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MasterState that = (MasterState) o;
        return Objects.equals(efficiencies, that.efficiencies) && Objects.equals(availableQuantities, that.availableQuantities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(efficiencies, availableQuantities);
    }
}