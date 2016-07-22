package com.pasho.osmjet;

/**
 * Created by Pavel on 28/06/2016.
 */
public class Consts {
    public static final int tileSize = 256;
    public static final int MinZoom = 14;
    public static final int MaxZoom = 18;

    public static int getMapSize() {
        return tileSize * 3;
    }

    public static int[] getMapMiddle() {
        int mid = getMapSize() / 2;
        return new int[]{mid, mid};
    }
}
