package model;

import jade.core.AID;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Request {

    String id;
    Boolean cascaded;
//    String originalId;
    long quantity;
    ResourceType resourceType;
    Map<Long, Long> utilityFunction;
    AID sender;
    AID originalSender;
    Set<Integer> allReceivers;
    // id, lifetime
    Map<String, Integer> reservedItems;

    public Request(String id, long quantity, ResourceType resourceType, Map<Long, Long> utilityFunction, AID sender) {
        this.id = id;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.utilityFunction = utilityFunction;
        this.sender = sender;
    }

    public Request(String id, Boolean cascaded, long quantity, ResourceType resourceType, Map<Long, Long> utilityFunction, AID sender, AID originalSender, Set<Integer> allReceivers, Map<String, Integer> reservedItems) {
        this.id = id;
        this.cascaded = cascaded;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.utilityFunction = utilityFunction;
        this.sender = sender;
        this.originalSender = originalSender;
        this.allReceivers = allReceivers;
        this.reservedItems = reservedItems;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Request request = (Request) o;
        return id.equals(request.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
