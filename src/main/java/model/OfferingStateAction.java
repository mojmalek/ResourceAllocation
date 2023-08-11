package model;


import java.util.Objects;

public class OfferingStateAction {

    public OfferingState state;
    public OfferingAction action;

    public OfferingStateAction(OfferingState state, OfferingAction action) {
        this.state = state;
        this.action = action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OfferingStateAction that = (OfferingStateAction) o;
        return Objects.equals(state, that.state) && Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, action);
    }
}
