package model;


import java.util.Objects;

public class MasterStateAction {

    public MasterState state;
    public MasterAction action;


    public MasterStateAction(MasterState state, MasterAction action) {
        this.state = state;
        this.action = action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MasterStateAction that = (MasterStateAction) o;
        return Objects.equals(state, that.state) && Objects.equals(action, that.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, action);
    }
}
