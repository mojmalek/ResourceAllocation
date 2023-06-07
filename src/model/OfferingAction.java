package model;


public class OfferingAction {

    // find possible actions in a state
    // an action is to select a request and an offerQuantity <= remainingQuantity

    public ResourceType resourceType;

    public Request selectedRequest;

    public long offerQuantity;

    public OfferingAction(ResourceType resourceType, Request selectedRequest, long offerQuantity) {
        this.resourceType = resourceType;
        this.selectedRequest = selectedRequest;
        this.offerQuantity = offerQuantity;
    }
}
