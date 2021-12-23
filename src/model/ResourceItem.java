package model;

import java.util.Comparator;
import java.util.Date;
import java.util.Objects;

public class ResourceItem {

    private String id;
    private ResourceType type;
//    private Date expiryDate;
    private int lifetime;


    public ResourceItem(String id, ResourceType type, int lifetime) {
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

    public int getLifetime() {
        return lifetime;
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
            Integer lifetime1 = item1.lifetime;
            Integer lifetime2 = item2.lifetime;
            return lifetime1.compareTo(lifetime2);
        }
    }

}
