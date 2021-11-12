package model;

import java.util.Date;

public class ResourceItem {

    private ResourceType type;
    private Date expiryDate;


    public ResourceItem(ResourceType type, Date expiryDate) {
        this.type = type;
        this.expiryDate = expiryDate;
    }

    public ResourceType getType() {
        return type;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

}
