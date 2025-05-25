package model;

import jade.core.AID;

import java.util.Comparator;
import java.util.Objects;

public class ResourceItem {

    private String id;
    private ResourceType type;
    private long expiryTime;
    private AID manager;


    public ResourceItem(String id, ResourceType type, long expiryTime) {
        this.id = id;
        this.type = type;
        this.expiryTime = expiryTime;
    }

    public ResourceItem(String id, ResourceType type, long expiryTime, AID manager) {
        this.id = id;
        this.type = type;
        this.expiryTime = expiryTime;
        this.manager = manager;
    }

    public String getId() {
        return id;
    }

    public ResourceType getType() {
        return type;
    }

    public long getExpiryTime() {
        return expiryTime;
    }

    public AID getManager() {
        return manager;
    }

    public void setExpiryTime(long expiryTime) {
        this.expiryTime = expiryTime;
    }

    @Override
    public String toString() {
        return "ResourceItem{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", manager=" + manager.getLocalName() +
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


    public static class resourceItemComparator implements Comparator<ResourceItem> {
        public int compare(ResourceItem item1, ResourceItem item2)
        {
            Long expiryTime1 = item1.expiryTime;
            Long expiryTime2 = item2.expiryTime;
            String id1 = item1.id;
            String id2 = item2.id;

            if (expiryTime1.equals(expiryTime2)) {
                return id1.compareTo(id2);
            } else {
                return expiryTime1.compareTo(expiryTime2);
            }
        }
    }

}
