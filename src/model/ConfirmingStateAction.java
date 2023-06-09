package model;


import java.util.Objects;

public class ConfirmingStateAction {

    public ConfirmingState state;
    public ConfirmingAction action;

    public ConfirmingStateAction(ConfirmingState state, ConfirmingAction action) {
        this.state = state;
        this.action = action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfirmingStateAction that = (ConfirmingStateAction) o;
        return Objects.equals(state, that.state) && Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, action);
    }
}
