package com.pasho.osmjet;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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

    public int getZoom() {
        return zoom;
    }

    public void setZoom(int zoom) {
        if (this.zoom == zoom) return;

        int deltaZoom = zoom - this.zoom;

        this.zoom = zoom;

        int scaledMapSize = (int)(Consts.getMapSize() * Math.pow(2, deltaZoom));

        Bitmap scaledMapBitmap = Bitmap.createScaledBitmap(currentBitmap, scaledMapSize, scaledMapSize, true);

        if(deltaZoom > 0){
            currentBitmap = Bitmap.createBitmap(scaledMapBitmap, Consts.getMapSize() / 2, Consts.getMapSize() / 2, Consts.getMapSize(), Consts.getMapSize());
        }
        else{
            currentBitmap = Bitmap.createBitmap(Consts.getMapSize(), Consts.getMapSize(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(currentBitmap);
            canvas.drawBitmap(scaledMapBitmap, Consts.tileSize / 2, Consts.tileSize / 2, null);
        }

        consumer.onMapBitmap(currentBitmap);

        downloadTiles();
    }

    private final static char[] Servers = new char[]{'a', 'b', 'c'};

    public MapBitmapManager(IMapBitmapConsumer consumer, HUDConnectivityManager connectivityManager) {

        for (int i = 0; i < 9; i++) {
            currentBitmaps.add(null);
        }

        this.consumer = consumer;
        this.connectivityManager = connectivityManager;
    }

    @SuppressLint("DefaultLocale")
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

    private void downloadTiles() {
        currentDownloadAttempt++;

        for (DownLoadTileTask task : downloadTasks) {
            task.cancel(true);
        }
        downloadTasks.clear();

        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int index = y * 3 + x;

                if (currentBitmaps.get(index) != null) continue;

                int tileX = this.currentCenterTileXy[0] + x - 1;
                int tileY = this.currentCenterTileXy[1] + y - 1;
                String url = getUrl(tileX, tileY);
                downloadTasks.add((DownLoadTileTask) new DownLoadTileTask(index, url, this.currentDownloadAttempt).execute());
            }
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
