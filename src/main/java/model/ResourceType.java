package model;

import java.util.Random;

public enum ResourceType {

    A, B, C, D, E, F, G, H, I, J;
//    A, B, C, D, E, F, G, H;
//    A, B, C, D, E, F;
//    A, B, C, D;
//    A, B;
//    A;

    private static final ResourceType[] VALUES = values();
    private static final int SIZE = VALUES.length;
    private static final Random RANDOM = new Random();

    public static ResourceType getRandom()  {
        return VALUES[RANDOM.nextInt(SIZE)];
    }

    public static int getSize()  {
        return SIZE;
    }

    public static ResourceType[] getValues() {
        return values();
    }
}
