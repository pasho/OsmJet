package com.pasho.osmjet;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.location.Location;
import android.location.LocationListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.connectivity.http.HUDHttpRequest;
import com.reconinstruments.os.connectivity.http.HUDHttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
    private Context context;

    public double getLon() {
        return lon;
    }

    public double getLat() {
        return lat;
    }

    public int getZoom() {
        return zoom;
    }

    public void goTo(int zoom, double lat, double lon){
        this.lat = lat;
        this.lon = lon;
        this.zoom = zoom;

        updateTilesPosition();
        downloadTiles();
    }

    public void setZoom(int zoom) {
        if (this.zoom == zoom) return;

        int oldZoom = this.zoom;
        int[] oldTileXy = this.currentCenterTileXy;

        this.zoom = zoom;

        updateTilesPosition();
        clearTiles();
        rescaleCurrentBitmap(oldZoom, oldTileXy);
        downloadTiles();
    }

    private void updateTilesPosition() {

        double actualX = (lon + 180) / 360 * (1 << zoom);
        double actualY = (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom);
        int tileX = (int) Math.floor(actualX);
        int tileY = (int) Math.floor(actualY);

        int viewerX = (int) (Consts.tileSize * (1 + (actualX - tileX)));
        int viewerY = (int) (Consts.tileSize * (1 + (actualY - tileY)));
        viewerPixelPosition = new int[] { viewerX,  viewerY };

        this.consumer.onViewerPosition(this.viewerPixelPosition);

        this.currentCenterTileXy = new int[]{tileX, tileY};
    }

    private void rescaleCurrentBitmap(int oldZoom, int[] oldTileXy) {
        int deltaZoom = zoom - oldZoom;
        double multiplier = Math.pow(2, deltaZoom);

        if(deltaZoom > 0){//in

            int oldAreaLeft = (oldTileXy[0] - 1) * Consts.tileSize;
            int oldAreaTop = (oldTileXy[1] - 1) * Consts.tileSize;

            int newAreaLeft = (int) (((currentCenterTileXy[0] - 1) * Consts.tileSize) / multiplier);
            int newAreaTop = (int) (((currentCenterTileXy[1] - 1) * Consts.tileSize) / multiplier);

            int newAreaSize = (int) (Consts.getMapSize() / multiplier);
            Bitmap newArea = Bitmap.createBitmap(currentBitmap, newAreaLeft - oldAreaLeft, newAreaTop - oldAreaTop, newAreaSize, newAreaSize);
            currentBitmap = Bitmap.createScaledBitmap(newArea, Consts.getMapSize(), Consts.getMapSize(), true);
        }
        else{//out
            int scaledMapSize = (int)(Consts.getMapSize() * multiplier);

            int newAreaLeft = (currentCenterTileXy[0] - 1) * Consts.tileSize;
            int newAreaTop = (currentCenterTileXy[1] - 1) * Consts.tileSize;

            int oldAreaLeft = (int)((oldTileXy[0] - 1) * Consts.tileSize * multiplier);
            int oldAreaTop = (int)((oldTileXy[1] - 1) * Consts.tileSize * multiplier);

            Bitmap scaledMapBitmap = Bitmap.createScaledBitmap(currentBitmap, scaledMapSize, scaledMapSize, true);

            currentBitmap = Bitmap.createBitmap(Consts.getMapSize(), Consts.getMapSize(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(currentBitmap);
            canvas.drawBitmap(scaledMapBitmap, oldAreaLeft - newAreaLeft, oldAreaTop - newAreaTop, null);
        }

        consumer.onMapBitmap(currentBitmap);
    }

    private void clearTiles() {
        for (int i = 0; i < currentBitmaps.size(); i++) {
            currentBitmaps.set(i, null);
        }
    }

    public MapBitmapManager(IMapBitmapConsumer consumer, Context context, HUDConnectivityManager connectivityManager) {

        for (int i = 0; i < 9; i++) {
            currentBitmaps.add(null);
        }

        this.consumer = consumer;
        this.context = context;
        this.connectivityManager = connectivityManager;
    }

    @Override
    public void onLocationChanged(Location location) {
        lon = location.getLongitude();
        lat = location.getLatitude();

        int[] oldCenterTileXy = this.currentCenterTileXy;

        updateTilesPosition();

        if (oldCenterTileXy[0] == this.currentCenterTileXy[0] && oldCenterTileXy[1] == this.currentCenterTileXy[1])
            return;

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

            TileData tileData = new TileData(MapSources.openCycleMap, zoom, tileX, tileY);

            if(!tryLoadFromCache(index, tileData)){
                DownLoadTileTask task = new DownLoadTileTask(index, tileData, this.currentDownloadAttempt);
                downloadTasks.add(task);
                task.execute();
            }
        }
    }

    private boolean tryLoadFromCache(int index, TileData tileData) {
        //return false;
        String cachedFilePath = MapSources.getCachePath(tileData);

        File cachedFile = new File(context.getCacheDir(), cachedFilePath);

        if(!cachedFile.exists()) return false;

        Bitmap bitmap = BitmapFactory.decodeFile(cachedFile.getAbsolutePath());

        currentBitmaps.set(index, bitmap);
        createAndPostBitmap();

        Log.d(TAG, "Loaded from cache: " + bitmap.getWidth() + " " + bitmap.getHeight() + " " + cachedFile.getAbsolutePath());

        return true;
    }

    private void onTileDownloaded(Bitmap bitmap, int index, TileData tileData, int attempt) {
        try {
            String cachedFilePath = MapSources.getCachePath(tileData);

            File cachedFile = new File(context.getCacheDir(), cachedFilePath);

            cachedFile.getParentFile().mkdirs();
            cachedFile.createNewFile();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();

            FileOutputStream fos = new FileOutputStream(cachedFile);
            fos.write(bitmapdata);
            fos.flush();
            fos.close();

            Log.d(TAG, "Cached: " + cachedFile.getAbsolutePath());
        }
        catch (Exception ex){
            Log.e(TAG, "Failed to store tile in cache: " + ex.getMessage());
        }

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
        private TileData tileData;
        private String url;
        private int attempt;

        public DownLoadTileTask(int index, TileData tileData, int attempt) {
            this.index = index;
            this.tileData = tileData;
            this.url = MapSources.getUrl(tileData);
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
            onTileDownloaded(bitmap, this.index, this.tileData, this.attempt);
        }
    }
}
