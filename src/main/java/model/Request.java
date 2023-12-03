package model;

import jade.core.AID;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Request {

    public String id;
    public String previousId;
    public String originalId;
    public Boolean cascaded;
    public long quantity;
    public ResourceType resourceType;
    public Map<Long, Long> utilityFunction;
    public AID sender;
    public AID originalSender;
    public Set<Integer> previousReceivers;
    public Set<Integer> allReceivers;
    // id, lifetime
    public Map<String, Long> reservedItems;
    public long timeSent;
    public long timeout;
    public Long originalTimeout;
    public boolean processed;


    public Request(String id, long quantity, ResourceType resourceType, Map<Long, Long> utilityFunction, AID sender) {
        this.id = id;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.utilityFunction = utilityFunction;
        this.sender = sender;
    }

    public Request(String id, Boolean cascaded, long quantity, ResourceType resourceType, Map<Long, Long> utilityFunction, AID sender, AID originalSender, Set<Integer> allReceivers, Map<String, Long> reservedItems) {
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

    public Request(String id, String previousId, String originalId, Boolean cascaded, long quantity, ResourceType resourceType, Map<Long, Long> utilityFunction, AID sender, AID originalSender, Set<Integer> allReceivers, Map<String, Long> reservedItems, long timeSent, long timeout, Long originalTimeout) {
        this.id = id;
        this.previousId = previousId;
        this.originalId = originalId;
        this.cascaded = cascaded;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.utilityFunction = utilityFunction;
        this.sender = sender;
        this.originalSender = originalSender;
        this.allReceivers = allReceivers;
        this.reservedItems = reservedItems;
        this.timeSent = timeSent;
        this.timeout = timeout;
        this.originalTimeout = originalTimeout;
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
