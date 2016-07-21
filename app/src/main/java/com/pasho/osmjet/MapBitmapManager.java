package com.pasho.osmjet;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.connectivity.http.HUDHttpRequest;
import com.reconinstruments.os.connectivity.http.HUDHttpResponse;

import java.util.ArrayList;
import java.util.HashSet;

public class MapBitmapManager implements LocationListener {

    private final String TAG = this.getClass().getSimpleName();

    private final IMapBitmapConsumer consumer;
    private final HUDConnectivityManager connectivityManager;

    private int[] currentCenterTileXy = {0, 0};
    private int[] viewerPixelPosition = {0, 0};
    private int zoom = Consts.MinZoom;
    private int currentDownloadAttempt = 0;
    private ArrayList<Bitmap> currentBitmaps = new ArrayList<Bitmap>();
    private Bitmap currentBitmap = Bitmap.createBitmap(Consts.getMapSize(), Consts.getMapSize(), Bitmap.Config.ARGB_8888);
    private double lon;
    private double lat;

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        if (this.zoom == zoom) return;

        int oldZoom = this.zoom;
        int[] oldTileXy = this.currentCenterTileXy;

        this.zoom = zoom;

        double actualX = (lon + 180) / 360 * (1 << zoom);
        double actualY = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom);
        int tileX = (int) Math.floor(actualX);
        int tileY = (int) Math.floor(actualY);

        int viewerX = (int) (Consts.tileSize * (1 + (actualX - tileX)));
        int viewerY = (int) (Consts.tileSize * (1 + (actualY - tileY)));
        viewerPixelPosition = new int[] { viewerX,  viewerY };

        this.consumer.onViewerPosition(this.viewerPixelPosition);

        if (tileX == this.currentCenterTileXy[0] && tileY == this.currentCenterTileXy[1])
            return;

        this.currentCenterTileXy = new int[]{tileX, tileY};

        for (int i = 0; i < currentBitmaps.size(); i++) {
            currentBitmaps.set(i, null);
        }
        downloadTiles();

        int deltaZoom = zoom - oldZoom;


        double multiplier = Math.pow(2, deltaZoom);
        int scaledMapSize = (int)(Consts.getMapSize() * multiplier);
        int oldAreaLeft = (int)((oldTileXy[0] - 1) * Consts.tileSize * multiplier);
        int oldAreaTop = (int)((oldTileXy[1] - 1) * Consts.tileSize * multiplier);

        int newAreaLeft = (currentCenterTileXy[0] - 1) * Consts.tileSize;
        int newAreaTop = (currentCenterTileXy[1] - 1) * Consts.tileSize;

        //TODO: optimize - scale only the area, which will remain
        Bitmap scaledMapBitmap = Bitmap.createScaledBitmap(currentBitmap, scaledMapSize, scaledMapSize, true);

        if(deltaZoom > 0){//in
            currentBitmap = Bitmap.createBitmap(scaledMapBitmap, newAreaLeft - oldAreaLeft, newAreaTop - oldAreaTop, Consts.getMapSize(), Consts.getMapSize());
        }
        else{//out
            currentBitmap = Bitmap.createBitmap(Consts.getMapSize(), Consts.getMapSize(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(currentBitmap);
            canvas.drawBitmap(scaledMapBitmap, oldAreaLeft - newAreaLeft, oldAreaTop - newAreaTop, null);
        }

        consumer.onMapBitmap(currentBitmap);
    }

    private final static char[] Servers = new char[]{'a', 'b', 'c'};

    public MapBitmapManager(IMapBitmapConsumer consumer, HUDConnectivityManager connectivityManager) {

        for (int i = 0; i < 9; i++) {
            currentBitmaps.add(null);
        }

        this.consumer = consumer;
        this.connectivityManager = connectivityManager;
    }

    int serversRotor = 0;

    @SuppressLint("DefaultLocale")
    private String getUrl(int x, int y) {
        char server = Servers[serversRotor];
        serversRotor = (serversRotor + 1) % Servers.length;

        return String.format("http://%1$c.tile.opencyclemap.org/cycle/%2$d/%3$d/%4$d.png", server, zoom, x, y);
    }

    @Override
    public void onLocationChanged(Location location) {
        lon = location.getLongitude();
        lat = location.getLatitude();

        double actualX = (lon + 180) / 360 * (1 << zoom);
        double actualY = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom);
        int tileX = (int) Math.floor(actualX);
        int tileY = (int) Math.floor(actualY);

        this.viewerPixelPosition = new int[]{
            (int) (Consts.tileSize * (1 + (actualX - tileX))),
            (int) (Consts.tileSize * (1 + (actualY - tileY)))
        };

        this.consumer.onViewerPosition(this.viewerPixelPosition);

        if (tileX == this.currentCenterTileXy[0] && tileY == this.currentCenterTileXy[1])
            return;

        int[] oldCenterTileXy = this.currentCenterTileXy;

        this.currentCenterTileXy = new int[]{tileX, tileY};

        tryReuseCurrentTiles(oldCenterTileXy);
        downloadTiles();
    }

    private void tryReuseCurrentTiles(int[] oldCenterTileXy) {
        ArrayList<Bitmap> oldBitmaps = (ArrayList<Bitmap>) currentBitmaps.clone();
        int dx = currentCenterTileXy[0] - oldCenterTileXy[0];
        int dy = currentCenterTileXy[1] - oldCenterTileXy[1];

        for (int i = 0; i < 9; i++) {
            int newGridX = i % 3;
            int newGridY = i / 3;

            int oldGridX = newGridX + dx;
            int oldGridY = newGridY + dy;

            if (oldGridX > 2 || oldGridX < 0 || oldGridY < 0 || oldGridY > 2) {
                currentBitmaps.set(i, null);
            } else {
                int oldTileIndex = oldGridY * 3 + oldGridX;
                currentBitmaps.set(i, oldBitmaps.get(oldTileIndex));
            }
        }

        createAndPostBitmap();
    }

    private HashSet<DownLoadTileTask> downloadTasks = new HashSet<DownLoadTileTask>();
    int[][] downloadOrder = {
            {1, 1},
            {0, 1},
            {1, 0},
            {2, 1},
            {1, 2},
            {0, 0},
            {2, 0},
            {0, 2},
            {2, 2}
    };

    private void downloadTiles() {
        currentDownloadAttempt++;

        for (DownLoadTileTask task : downloadTasks) {
            task.cancel(true);
        }
        downloadTasks.clear();

        for(int[] xy : downloadOrder){
            int x = xy[0];
            int y = xy[1];

            int index = y * 3 + x;

            if (currentBitmaps.get(index) != null) continue;

            int tileX = this.currentCenterTileXy[0] + x - 1;
            int tileY = this.currentCenterTileXy[1] + y - 1;
            String url = getUrl(tileX, tileY);
            downloadTasks.add((DownLoadTileTask) new DownLoadTileTask(index, url, this.currentDownloadAttempt).execute());
        }
    }

    private void onTileDownloaded(Bitmap bitmap, int index, int attempt) {
        if (attempt != currentDownloadAttempt) return;

        currentBitmaps.set(index, bitmap);
        createAndPostBitmap();
    }

    private void createAndPostBitmap() {
        //Bitmap mapBitmap = Bitmap.createBitmap(Consts.getMapSize(), Consts.getMapSize(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(currentBitmap);
        for (int i = 0; i < 9; i++) {
            int x = i % 3;
            int y = i / 3;

            Bitmap bitmap = this.currentBitmaps.get(i);
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, x * Consts.tileSize, y * Consts.tileSize, null);
            }
        }

        consumer.onMapBitmap(currentBitmap);
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
            Log.d(TAG, url);
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
            downloadTasks.remove(this);
            onTileDownloaded(bitmap, this.index, this.attempt);
        }
    }
}
