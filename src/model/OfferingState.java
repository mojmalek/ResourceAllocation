package model;

import jade.core.AID;

import java.util.Map;

public class OfferingState {

    ResourceType resourceType;

    // a matrix of potential requests with their utilities for each neighbor
    //Integer[][] requestUtils;
    Map<AID, Map<Long, Long>> requestUtils;

    long remainingQuantity;
}
