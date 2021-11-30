package model;

import java.util.ArrayList;

public class AvailableResource {

    private ResourceType type;
    private ArrayList<ResourceItem> resourceItems;

    public AvailableResource(ResourceType type, ArrayList<ResourceItem> resourceItems) {
        this.type = type;
        this.resourceItems = resourceItems;
    }


    public ResourceType getType() {
        return type;
    }

    public ArrayList<ResourceItem> getResourceItems() {
        return resourceItems;
    }

    @Override
    public String toString() {
        return "AvailableResource{" +
                "type=" + type +
                ", resourceItems=" + resourceItems +
                '}';
    }
}
