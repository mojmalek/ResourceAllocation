package model;

import java.util.Date;
import java.util.Objects;

public class ResourceItem {

    private String id;
    private ResourceType type;
    private Date expiryDate;


    public ResourceItem(String id, ResourceType type, Date expiryDate) {
        this.id = id;
        this.type = type;
        this.expiryDate = expiryDate;
    }

    public String getId() {
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
                "id='" + id + '\'' +
                ", type=" + type +
                ", expiryDate=" + expiryDate +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResourceItem that = (ResourceItem) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
