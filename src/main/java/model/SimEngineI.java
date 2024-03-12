package model;

import jade.core.Agent;

import java.util.Map;
import java.util.SortedSet;

public interface SimEngineI {

    SortedSet<Task> findTasks(Agent myAgent, int episode);
    Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent, int episode);

}
