package model;

import jade.core.AID;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Request2 {

    String id;
    String originalId;
    long quantity;
    ResourceType resourceType;
    Map<Long, Long> utilityFunction;
    AID sender;
    Set<Integer> receivers;
    // id, lifetime
    Map<String, Integer> reservedItems;

    public Request2(String id, String originalId, long quantity, ResourceType resourceType, Map<Long, Long> utilityFunction, AID sender, Set<Integer> receivers, Map<String, Integer> reservedItems) {
        this.id = id;
        this.originalId = originalId;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.utilityFunction = utilityFunction;
        this.sender = sender;
        this.receivers = receivers;
        this.reservedItems = reservedItems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request2 request = (Request2) o;
        return id.equals(request.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
