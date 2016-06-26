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

import java.util.Locale;

public class TilesManager implements LocationListener {

    private final String TAG = this.getClass().getSimpleName();

    private final ITilesConsumer consumer;
    private final HUDConnectivityManager connectivityManager;

    private int x = 0;
    private int y = 0;
    private int zoom = 18;

    private final static char[] Servers = new char[]{'a', 'b', 'c'};

    public Bitmap[] getTileBitmaps() {
        return tileBitmaps;
    }

    private Bitmap[] tileBitmaps;

    public TilesManager(ITilesConsumer consumer, HUDConnectivityManager connectivityManager) {
        this.consumer = consumer;
        this.connectivityManager = connectivityManager;
    }

    private String getUrl(int x, int y) {
        double rand = Math.random();
        char server = Servers[(int) Math.floor(rand * 3)];
        return String.format(Locale.US, "http://%1$c.tile2.opencyclemap.org/transport/%2$d/%3$d/%4$d.png", server, zoom, x, y);
    }

    @Override
    public void onLocationChanged(Location location) {
        double lon = location.getLongitude();
        double lat = location.getLatitude();

        int x = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int y = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));

        if (x == this.x && y == this.y)
            return;

        this.x = x;
        this.y = y;

        Bitmap[] bitmaps = new Bitmap[9];
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                String url = getUrl(x + j, y + i);
                int n = (i + 1) * 3 + (j + 1);
                new DownLoadTileTask(bitmaps, n, url).execute();
            }
        }
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

    private void onAllTilesDownloaded(Bitmap[] bitmaps) {
        tileBitmaps = bitmaps;
        consumer.onTiles(bitmaps);
    }

    private class DownLoadTileTask extends AsyncTask<Void, Void, Bitmap> {

        private final Bitmap[] _bitmaps;
        private final int _position;
        private String url;

        public DownLoadTileTask(Bitmap[] container, int position, String url) {
            this._bitmaps = container;
            this._position = position;
            this.url = url;
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
        protected void onPostExecute(Bitmap result) {
            _bitmaps[_position] = result;

            boolean hasHoles = false;
            for (Bitmap b : _bitmaps) {
                if (b == null) {
                    hasHoles = true;
                    break;
                }
            }

            if (!hasHoles) {
                onAllTilesDownloaded(_bitmaps);
            }
        }
    }
}
