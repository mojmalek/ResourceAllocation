package model;

import jade.core.AID;
import org.deeplearning4j.rl4j.observation.Observation;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ConfirmingState {

    public ResourceType resourceType;

    public Request request;

    public Map<Long, Long> requestUtilFunction;

    public Set<Offer> offers;

    public Map<AID, Map<Long, Long>> offerCosts;

//    public long currentConfirmedQuantity;
    public long remainingRequestedQuantity;

    public Observation observation;

    public Set<ConfirmingAction> possibleActions;


    public ConfirmingState(ResourceType resourceType, Request request, Map<Long, Long> requestUtilFunction, Set<Offer> offers, Map<AID, Map<Long, Long>> offerCosts, long remainingRequestedQuantity) {
        this.resourceType = resourceType;
        this.request = request;
        this.requestUtilFunction = requestUtilFunction;
        this.offers = offers;
        this.offerCosts = offerCosts;
        this.remainingRequestedQuantity = remainingRequestedQuantity;
    }

    public ConfirmingState(Observation observation, Set<ConfirmingAction> possibleActions) {
        this.observation = observation;
        this.possibleActions = possibleActions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmingState that = (ConfirmingState) o;
        return resourceType == that.resourceType && Objects.equals(offerCosts, that.offerCosts) && Objects.equals(requestUtilFunction, that.requestUtilFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, offerCosts, requestUtilFunction);
    }
}
