package model;


import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OfferingAction that = (OfferingAction) o;
        return offerQuantity == that.offerQuantity && resourceType == that.resourceType && Objects.equals(selectedRequest.sender, that.selectedRequest.sender);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, selectedRequest.sender, offerQuantity);
    }
}
