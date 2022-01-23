package model;

import jade.core.AID;

import java.util.Objects;

public class Confirmation {

    String id;
    String bidId;
    int quantity;
    ResourceType resourceType;
    AID sender;
    AID receiver;

    public Confirmation(String id, String bidId, int quantity, ResourceType resourceType, AID sender, AID receiver) {
        this.id = id;
        this.bidId = bidId;
        this.quantity = quantity;
        this.resourceType = resourceType;
        this.sender = sender;
        this.receiver = receiver;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Confirmation confirmation = (Confirmation) o;
        return id.equals(confirmation.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
