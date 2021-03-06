package com.pasho.osmjet;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;

import com.reconinstruments.os.HUDOS;
import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.hardware.sensors.HUDHeadingManager;
import com.reconinstruments.os.hardware.sensors.HeadLocationListener;

import java.io.File;

public class MapActivity extends Activity implements HeadLocationListener, IMapBitmapConsumer {
    private final String TAG = this.getClass().getSimpleName();

    private HUDHeadingManager headingManager;
    private ImageView imageView;
    private LocationManager locationService;
    private MapBitmapManager mapBitmapManager;

    private float mapRot = 0;
    private int[] mapPos = {0, 0};

    void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        File cache =new File(getCacheDir(), MapSources.openCycleMap);
//        if(cache.exists()){
//            deleteRecursive(cache);
//            Log.d(TAG, "Deleted cache");
//        }

        setContentView(R.layout.image_layout);

        imageView = (ImageView) findViewById(R.id.imageView);

        HUDConnectivityManager connectivityManager = (HUDConnectivityManager) HUDOS.getHUDService(HUDOS.HUD_CONNECTIVITY_SERVICE);
        locationService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        headingManager = (HUDHeadingManager) HUDOS.getHUDService(HUDOS.HUD_HEADING_SERVICE);
        mapBitmapManager = new MapBitmapManager(this, this, connectivityManager);

        System.load("/system/lib/libreconinstruments_jni.so");

        SharedPreferences preferences = getPreferences(0);

        if(preferences.getBoolean("hasPosition", false)) {
            int zoom = preferences.getInt("zoom", 14);
            double lat = preferences.getFloat("lat", 0);
            double lon = preferences.getFloat("lon", 0);

            mapBitmapManager.goTo(zoom, lat, lon);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationService.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, mapBitmapManager);
//        Location loc = new Location("GPS_PROVIDER");
//        loc.setLatitude(mapBitmapManager.getLat());
//        loc.setLongitude(mapBitmapManager.getLon());
//        new SouthMover(loc, this).register(mapBitmapManager);
        headingManager.register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        locationService.removeUpdates(mapBitmapManager);
        headingManager.unregister(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences preferences = getPreferences(0);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("zoom", mapBitmapManager.getZoom());
        editor.putFloat("lat", (float) mapBitmapManager.getLat());
        editor.putFloat("lon", (float) mapBitmapManager.getLon());
        editor.putBoolean("hasPosition", true);

        editor.commit();
    }

    @Override
    public void onHeadLocation(float yaw, float pitch, float roll) {
        float rotation = -yaw;
        if (Math.abs(rotation - mapRot) < 5)
            return;

        mapRot = rotation;

        alignMap();
    }

    private void alignMap() {
        Matrix matrix = new Matrix();

        int vcx = imageView.getWidth() / 2;
        int vcy = imageView.getHeight() / 2;

        int dx = vcx - mapPos[0];
        int dy = vcy - mapPos[1];

        matrix.setTranslate(dx, dy);
        matrix.postRotate(mapRot, imageView.getWidth() / 2, imageView.getHeight() / 2);

        imageView.setImageMatrix(matrix);
    }

    @Override
    public void onMapBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }

    @Override
    public void onViewerPosition(int[] xy) {
        if(Math.abs(mapPos[0] - xy[0]) + Math.abs(mapPos[1] - xy[1]) < 2)
            return;

        mapPos = xy;

        alignMap();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode)
        {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                switchZoom();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                zoomOut();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                zoomIn();
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void zoomIn() {
        mapBitmapManager.setZoom(Math.min(mapBitmapManager.getZoom() + 1, Consts.MaxZoom));
    }

    private void zoomOut() {
        mapBitmapManager.setZoom(Math.max(mapBitmapManager.getZoom() - 1, Consts.MinZoom));
    }

    private void switchZoom() {
        if(mapBitmapManager.getZoom() == Consts.MaxZoom)
            mapBitmapManager.setZoom(Consts.MinZoom);
        else
            zoomIn();
    }
}