package model;


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
}
