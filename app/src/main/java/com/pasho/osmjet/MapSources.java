package com.pasho.osmjet;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Pavel on 23/07/2016.
 */
public class MapSources{
    public static final String openCycleMap = "cycle";
    public static final char[] servers = new char[]{'a', 'b', 'c'};

    public static final Map<String, String> urlTemplates = new HashMap<String, String>();
    public static final Map<String, String> cachePathTemplates = new HashMap<String, String>();
    public static final Map<String, Integer> serverRotors = new HashMap<String, Integer>();

    static {
        urlTemplates.put(openCycleMap, "http://%1$c.tile.opencyclemap.org/cycle/%2$d/%3$d/%4$d.png");
        cachePathTemplates.put(openCycleMap, "cycle/%1$d/%2$d/%3$d.png");

        serverRotors.put(openCycleMap, 0);
    }

    public static String getUrl(TileData tileData){

        Integer serverRotor = serverRotors.get(tileData.getSource());
        char server = servers[serverRotor];
        serverRotors.put(tileData.getSource(), (serverRotor + 1) % servers.length);

        return String.format(urlTemplates.get(tileData.getSource()), server, tileData.getZoom(), tileData.getX(), tileData.getY());
    }

    public static String getCachePath(TileData tileData){
        return String.format(cachePathTemplates.get(tileData.getSource()), tileData.getZoom(), tileData.getX(), tileData.getY());
    }
}