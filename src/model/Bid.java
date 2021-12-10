package model;

import java.util.Map;
import java.util.Objects;

public class Bid {

    String id;
    long quantity;
    ResourceType resourceType;
    Map<Integer, Integer> costFunction;

    public Bid(String id, long quantity, ResourceType resourceType, Map<Integer, Integer> costFunction) {
        this.id = id;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.costFunction = costFunction;
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
