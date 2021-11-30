package model;

public class RequiredResource {

    private ResourceType type;
    private int quantity;
//    private TimeInterval timeInterval;


    public RequiredResource(ResourceType type, int quantity) {
        this.type = type;
        this.quantity = quantity;
    }


    public ResourceType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    @Override
    public String toString() {
        return "RequiredResource{" +
                "type=" + type +
                ", quantity=" + quantity +
                '}';
    }
}
