package model;


public class OfferingStateAction {

    public OfferingState state;
    public OfferingAction action;

    public OfferingStateAction(OfferingState state, OfferingAction action) {
        this.state = state;
        this.action = action;
    }
}
