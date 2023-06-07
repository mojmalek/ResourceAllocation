package model;


public class ConfirmingStateAction {

    public ConfirmingState state;
    public ConfirmingAction action;

    public ConfirmingStateAction(ConfirmingState state, ConfirmingAction action) {
        this.state = state;
        this.action = action;
    }
}
