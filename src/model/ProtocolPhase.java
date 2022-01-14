package model;

public enum ProtocolPhase {

    REQUESTING, BIDDING, CONFORMING;


    private static final ProtocolPhase[] VALUES = values();
    private static final int SIZE = VALUES.length;

    public static int getSize()  {
        return SIZE;
    }

    public static ProtocolPhase[] getValues() {
        return values();
    }
}
