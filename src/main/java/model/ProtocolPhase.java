package model;

public enum ProtocolPhase {

    REQUESTING, CASCADING_REQUEST, OFFERING, CASCADING_OFFER, CONFORMING, CASCADING_CONFIRM;


    private static final ProtocolPhase[] VALUES = values();
    private static final int SIZE = VALUES.length;

    public static int getSize()  {
        return SIZE;
    }

    public static ProtocolPhase[] getValues() {
        return values();
    }
}
