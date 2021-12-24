package model;

import jade.core.AID;
import jade.lang.acl.ACLMessage;

import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

public class Bid {

    String id;
    String reqId;
    long quantity;
    ResourceType resourceType;
    Map<Integer, Integer> costFunction;
    Map<String, Integer> offeredItems;
    AID sender;
    AID receiver;

    public Bid(String id, String reqId, long quantity, ResourceType resourceType, Map<Integer, Integer> costFunction, Map<String, Integer> offeredItems, AID sender, AID receiver) {
        this.id = id;
        this.reqId = reqId;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.costFunction = costFunction;
        this.offeredItems = offeredItems;
        this.sender = sender;
        this.receiver = receiver;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bid bid = (Bid) o;
        return id.equals(bid.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
