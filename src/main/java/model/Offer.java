package model;

import jade.core.AID;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Offer {

    public String id;
    public String reqId;
    public String originalReqId;
    public Boolean cascaded;
    public long quantity;
    public ResourceType resourceType;
    public Map<Long, Long> costFunction;
    // id, lifetime/managerId
    public Map<String, Long> offeredItems;
    // id, lifetime/managerId
    public Map<String, Long> reservedItems;
    public AID sender;
    public AID receiver;
    public Set<Offer> includedOffers;
    public long timeout;
    public OfferingStateAction currentStateAction;
    public OfferingState nextState;


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

    public Offer(String id, String reqId, String originalReqId, Boolean cascaded, long quantity, ResourceType resourceType, Map<Long, Long> costFunction, Map<String, Long> offeredItems, Map<String, Long> reservedItems, AID sender, AID receiver, Set<Offer> includedOffers, long timeout) {
        this.id = id;
        this.reqId = reqId;
        this.originalReqId = originalReqId;
        this.cascaded = cascaded;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.costFunction = costFunction;
        this.offeredItems = offeredItems;
        this.reservedItems = reservedItems;
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
