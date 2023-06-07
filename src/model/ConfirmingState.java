package model;

import jade.core.AID;

import java.util.Map;

public class ConfirmingState {

    public ResourceType resourceType;

    public Map<AID, Map<Long, Long>> offerCosts;

    public Map<Long, Long> requestUtilFunction;

    public long currentConfirmedQuantity;

    public ConfirmingState(ResourceType resourceType, Map<AID, Map<Long, Long>> offerCosts, Map<Long, Long> requestUtilFunction, long currentConfirmedQuantity) {
        this.resourceType = resourceType;
        this.offerCosts = offerCosts;
        this.requestUtilFunction = requestUtilFunction;
        this.currentConfirmedQuantity = currentConfirmedQuantity;
    }
}
