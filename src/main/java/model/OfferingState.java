package model;

import jade.core.AID;
import org.deeplearning4j.rl4j.observation.Observation;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class OfferingState {

    public ResourceType resourceType;

    public ArrayList<Request> requests;

    // netUtil = request util - offer cost
    public Map<AID, Map<Long, Long>> netUtils;

    public long availableQuantity;

    public Observation observation;

    public Set<OfferingAction> possibleActions;

    public OfferingState(ResourceType resourceType, ArrayList<Request> requests, Map<AID, Map<Long, Long>> netUtils, long availableQuantity) {
        this.resourceType = resourceType;
        this.requests = requests;
        this.netUtils = netUtils;
        this.availableQuantity = availableQuantity;
    }

    public OfferingState(ResourceType resourceType, ArrayList<Request> requests, Observation observation, Set<OfferingAction> possibleActions) {
        this.resourceType = resourceType;
        this.requests = requests;
        this.observation = observation;
        this.possibleActions = possibleActions;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OfferingState that = (OfferingState) o;
        return resourceType == that.resourceType && Objects.equals(netUtils, that.netUtils);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, netUtils);
    }
}
