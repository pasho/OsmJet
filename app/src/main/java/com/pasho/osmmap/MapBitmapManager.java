package com.pasho.osmmap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;

import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.connectivity.http.HUDHttpRequest;
import com.reconinstruments.os.connectivity.http.HUDHttpResponse;

import java.util.ArrayList;

public class MapBitmapManager implements LocationListener {

    private final String TAG = this.getClass().getSimpleName();

    private final IMapBitmapConsumer consumer;
    private final HUDConnectivityManager connectivityManager;

    private int[] centerTileXy = {0, 0};
    private int[] viewerPixelPosition = {0, 0};
    private int zoom = 18;
    private int currentDownloadAttempt = 0;
    private ArrayList<Bitmap> currentBitmaps = new ArrayList<Bitmap>(9);

    private final static char[] Servers = new char[]{'a', 'b', 'c'};


    public Bitmap[] getTileBitmaps() {
        return tileBitmaps;
    }

    private Bitmap[] tileBitmaps;

    public MapBitmapManager(IMapBitmapConsumer consumer, HUDConnectivityManager connectivityManager) {
        this.consumer = consumer;
        this.connectivityManager = connectivityManager;
    }

    private String getUrl(int x, int y) {
        double rand = Math.random();
        char server = Servers[(int) Math.floor(rand * 3)];
//        return String.format(Locale.US, "http://%1$c.tile2.opencyclemap.org/transport/%2$d/%3$d/%4$d.png", server, zoom, x, y);
        return String.format("http://%1$c.tile.opencyclemap.org/cycle/%2$d/%3$d/%4$d.png", server, zoom, x, y);
    }

    @Override
    public void onLocationChanged(Location location) {
        double lon = location.getLongitude();
        double lat = location.getLatitude();

        double actualX = (lon + 180) / 360 * (1 << zoom);
        double actualY = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom);
        int tileX = (int) Math.floor(actualX);
        int tileY = (int) Math.floor(actualY);

        this.viewerPixelPosition = new int[]{
                (int) (Consts.tileSize * (1 + (actualX - tileX))),
                (int) (Consts.tileSize * (1 + (actualY - tileY)))
        };

        this.consumer.onViewerPosition(this.viewerPixelPosition);

        if (tileX == this.centerTileXy[0] && tileY == this.centerTileXy[1])
            return;

        this.centerTileXy = new int[]{tileX, tileY};

        downloadTiles();
    }

    private void downloadTiles() {

        this.currentDownloadAttempt++;
        this.currentBitmaps.clear();

        Bitmap[] bitmaps = new Bitmap[9];
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                String url = getUrl(this.centerTileXy[0] + j, this.centerTileXy[1] + i);
                int n = (i + 1) * 3 + (j + 1);
                new DownLoadTileTask(bitmaps, n, url, this.currentDownloadAttempt).execute();
            }
        }
    }

    private void onTileDownloaded(Bitmap bitmap, int index, int attempt){
        if(attempt != this.currentDownloadAttempt)
            return;

        this.currentBitmaps.add(index, bitmap);

        if(this.currentBitmaps.size() == 9)
            onAllTilesDownloaded();
    }
    
    private void onAllTilesDownloaded(Bitmap[] bitmaps, int[] xy) {
        tileBitmaps = bitmaps;
        consumer.onMapBitmap(bitmaps, xy);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    private class DownLoadTileTask extends AsyncTask<Void, Void, Bitmap> {

        private final int index;
        private String url;
        private int attempt;

        public DownLoadTileTask(int index, String url, int attempt) {
            this.index = index;
            this.url = url;
            this.attempt = attempt;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            Bitmap bitmapImg = null;
            try {
                //Http Get Request
                HUDHttpRequest request = new HUDHttpRequest(HUDHttpRequest.RequestMethod.GET, url);
                HUDHttpResponse response = connectivityManager.sendWebRequest(request);
                if (response.hasBody()) {
                    byte[] data = response.getBody();
                    bitmapImg = BitmapFactory.decodeByteArray(data, 0, data.length);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bitmapImg;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            onTileDownloaded(bitmap, this.index, this.attempt);
        }
    }
}
