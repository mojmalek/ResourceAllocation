package model;

import java.util.Map;
import java.util.Objects;

public class Request {

    String id;
    long quantity;
    ResourceType resourceType;
    Map<Integer, Integer> utilityFunction;

    public Request(String id, long quantity, ResourceType resourceType, Map<Integer, Integer> utilityFunction) {
        this.id = id;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.utilityFunction = utilityFunction;
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