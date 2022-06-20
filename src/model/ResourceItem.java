package model;

import java.util.Comparator;
import java.util.Date;
import java.util.Objects;

public class ResourceItem {

    private String id;
    private ResourceType type;
//    private Date expiryDate;
    private long lifetime;


    public ResourceItem(String id, ResourceType type, long lifetime) {
        this.id = id;
        this.type = type;
        this.lifetime = lifetime;
    }

    public String getId() {
        return id;
    }

    public ResourceType getType() {
        return type;
    }

    public long getLifetime() {
        return lifetime;
    }

    public void setLifetime(long lifetime) {
        this.lifetime = lifetime;
    }

    @Override
    public String toString() {
        return "ResourceItem{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", lifetime=" + lifetime +
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
            Long lifetime1 = item1.lifetime;
            Long lifetime2 = item2.lifetime;
            String id1 = item1.id;
            String id2 = item2.id;

            if (lifetime1.equals(lifetime2)) {
                return id1.compareTo(id2);
            } else {
                return lifetime1.compareTo(lifetime2);
            }
        }
    }

}
