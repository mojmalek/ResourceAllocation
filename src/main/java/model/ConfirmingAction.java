package model;


import java.util.Objects;

public class ConfirmingAction {

    // find possible actions in a state
    // an action is to select an offer and a valid confirmQuantity

    public ResourceType resourceType;

    public Offer selectedOffer;

    public long confirmQuantity;

    public ConfirmingAction(ResourceType resourceType, Offer selectedOffer, long confirmQuantity) {
        this.resourceType = resourceType;
        this.selectedOffer = selectedOffer;
        this.confirmQuantity = confirmQuantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmingAction action = (ConfirmingAction) o;
        return confirmQuantity == action.confirmQuantity && resourceType == action.resourceType && Objects.equals(selectedOffer.sender, action.selectedOffer.sender);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceType, selectedOffer.sender, confirmQuantity);
    }
}
