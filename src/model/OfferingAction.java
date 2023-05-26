package model;


public class OfferingAction {

    // find possible actions in a state
    // an action is to select a request and an offerQuantity <= remainingQuantity

    ResourceType resourceType;

    Request selectedRequest;

    long offerQuantity;
}
