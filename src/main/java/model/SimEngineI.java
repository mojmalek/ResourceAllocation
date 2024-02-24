package model;

import jade.core.Agent;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultWeightedEdge;

import java.util.Map;
import java.util.SortedSet;

public interface SimEngineI {

    SortedSet<Task> findTasks(Agent myAgent, int episode);
    Map<ResourceType, SortedSet<ResourceItem>> findResources (Agent myAgent, int episode);
    SortedSet<ResourceItem> findResourceItems( ResourceType resourceType, long lifeTime, long quantity, String agentName);
    Integer[][] generateRandomAdjacencyMatrix2(int numberOfAgents, int numberOfEdges);

}
