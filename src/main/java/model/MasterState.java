package model;


import jade.core.AID;
import org.deeplearning4j.rl4j.observation.Observation;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

public class MasterState {

    public SortedSet<Task> toDoTasks;

    public ArrayList<Double> efficiencies;

    public Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources;

    public Map<ResourceType, Long> availableQuantities;

    public Long currentAllocatedQuantity;

    public Observation observation;


    public MasterState(SortedSet<Task> toDoTasks, ArrayList<Double> efficiencies, Map<AID, Map<ResourceType, SortedSet<ResourceItem>>> agentAvailableResources, Map<ResourceType, Long> availableQuantities, Long currentAllocatedQuantity) {
        this.toDoTasks = toDoTasks;
        this.efficiencies = efficiencies;
        this.agentAvailableResources = agentAvailableResources;
        this.availableQuantities = availableQuantities;
        this.currentAllocatedQuantity = currentAllocatedQuantity;
    }

    public MasterState(Observation observation) {
        this.observation = observation;
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
        return Objects.equals(efficienciesSum(), that.efficienciesSum()) && Objects.equals(availableQuantities, that.availableQuantities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(efficienciesSum(), availableQuantities);
    }
}