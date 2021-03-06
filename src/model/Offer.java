package model;

import jade.core.AID;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Offer {

    String id;
    String reqId;
    long quantity;
    ResourceType resourceType;
    Map<Long, Long> costFunction;
    // id, lifetime
    Map<String, Long> offeredItems;
    AID sender;
    AID receiver;
    Set<Offer> includedOffers;
    long timeout;
    boolean processed;

    public Offer(String id, String reqId, long quantity, ResourceType resourceType, Map<Long, Long> costFunction, Map<String, Long> offeredItems, AID sender, AID receiver) {
        this.id = id;
        this.reqId = reqId;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.costFunction = costFunction;
        this.offeredItems = offeredItems;
        this.sender = sender;
        this.receiver = receiver;
    }

    public Offer(String id, String reqId, long quantity, ResourceType resourceType, Map<Long, Long> costFunction, Map<String, Long> offeredItems, AID sender, AID receiver, Set<Offer> includedOffers) {
        this.id = id;
        this.reqId = reqId;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.costFunction = costFunction;
        this.offeredItems = offeredItems;
        this.sender = sender;
        this.receiver = receiver;
        this.includedOffers = includedOffers;
    }

    public Offer(String id, String reqId, long quantity, ResourceType resourceType, Map<Long, Long> costFunction, Map<String, Long> offeredItems, AID sender, AID receiver, Set<Offer> includedOffers, long timeout) {
        this.id = id;
        this.reqId = reqId;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.costFunction = costFunction;
        this.offeredItems = offeredItems;
        this.sender = sender;
        this.receiver = receiver;
        this.includedOffers = includedOffers;
        this.timeout = timeout;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Offer offer = (Offer) o;
        return id.equals(offer.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
