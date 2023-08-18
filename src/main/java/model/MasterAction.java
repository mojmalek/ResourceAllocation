package model;


import jade.core.AID;

import java.util.Objects;

public class MasterAction {

    public Task selectedTask;

    public Double efficiency;

    public AID selectedAgent;


    public MasterAction(Task selectedTask, Double efficiency) {
        this.selectedTask = selectedTask;
        this.efficiency = efficiency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MasterAction that = (MasterAction) o;
        return Objects.equals(selectedTask.utility, that.selectedTask.utility);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selectedTask.utility);
    }
}