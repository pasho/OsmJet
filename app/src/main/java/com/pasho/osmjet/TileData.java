package com.pasho.osmjet;

/**
 * Created by Pavel on 23/07/2016.
 */
public class TileData {
    private int zoom;
    private int x;
    private int y;
    private String source;

    public TileData(String source, int zoom, int x, int y) {
        this.zoom = zoom;
        this.x = x;
        this.y = y;
        this.source = source;
    }

    public int getZoom() {
        return zoom;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getSource() {
        return source;
    }
}
