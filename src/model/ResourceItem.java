package model;

import java.util.Date;

public class ResourceItem {

    private int id;

    private ResourceType type;
    private Date expiryDate;


    public ResourceItem(ResourceType type, Date expiryDate) {
        this.type = type;
        this.expiryDate = expiryDate;
    }

    public int getId() {
        return id;
    }

    public ResourceType getType() {
        return type;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    @Override
    public String toString() {
        return "ResourceItem{" +
                "type=" + type +
                ", expiryDate=" + expiryDate +
                '}';
    }
}
