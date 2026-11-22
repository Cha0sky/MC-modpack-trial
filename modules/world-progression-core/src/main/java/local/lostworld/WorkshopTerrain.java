package local.lostworld;

import java.util.Arrays;

/** Heights are occupied ground blocks, including the two landings just outside the template. */
final class WorkshopTerrain {
    static final int UNSUITABLE = Integer.MIN_VALUE;
    static final int MAX_FILL = 3;
    private static final int MAX_CUT = 2;

    private WorkshopTerrain() {}

    static int floor(int[] ground, int front, int side) {
        if (front == UNSUITABLE || side == UNSUITABLE) return UNSUITABLE;
        Arrays.sort(ground);
        if (ground[0] == UNSUITABLE) return UNSUITABLE;
        int lowestFloor = Math.max(ground[ground.length - 1] - MAX_CUT, Math.max(front, side) - 1);
        int highestFloor = Math.min(ground[0] + MAX_FILL, Math.min(front, side) + 1);
        if (lowestFloor > highestFloor) return UNSUITABLE;
        return Math.clamp(ground[ground.length / 2], lowestFloor, highestFloor);
    }
}
