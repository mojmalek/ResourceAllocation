package model;

import jade.core.AID;

import java.util.Map;

public class OfferingState {

    public ResourceType resourceType;

    // netUtil = request util - offer cost
    public Map<AID, Map<Long, Long>> netUtils;

    public long availableQuantity;

    public OfferingState(ResourceType resourceType, Map<AID, Map<Long, Long>> netUtils, long availableQuantity) {
        this.resourceType = resourceType;
        this.netUtils = netUtils;
        this.availableQuantity = availableQuantity;
    }
}
