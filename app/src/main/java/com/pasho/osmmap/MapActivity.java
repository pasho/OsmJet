package com.pasho.osmmap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.reconinstruments.os.HUDOS;
import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.hardware.sensors.HUDHeadingManager;
import com.reconinstruments.os.hardware.sensors.HeadLocationListener;

public class MapActivity extends Activity implements HeadLocationListener, IMapBitmapConsumer {
    private final String TAG = this.getClass().getSimpleName();

    private HUDHeadingManager headingManager;
    private ImageView imageView;
    private LocationManager locationService;
    private MapBitmapManager tileManager;

    private float mapRot = 0;
    private int[] mapPos = {0, 0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.image_layout);

        imageView = (ImageView) findViewById(R.id.imageView);

        HUDConnectivityManager connectivityManager = (HUDConnectivityManager) HUDOS.getHUDService(HUDOS.HUD_CONNECTIVITY_SERVICE);
        locationService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        headingManager = (HUDHeadingManager) HUDOS.getHUDService(HUDOS.HUD_HEADING_SERVICE);
        tileManager = new MapBitmapManager(this, connectivityManager);

        System.loadLibrary("/system/lib/libreconinstruments_jni.so");
    }

    @Override
    protected void onResume() {
        super.onResume();

        locationService.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, tileManager);
        headingManager.register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        locationService.removeUpdates(tileManager);
        headingManager.unregister(this);
    }

    @Override
    public void onHeadLocation(float yaw, float pitch, float roll) {
        float rotation = -yaw;
        if (Math.abs(rotation - mapRot) < 5)
            return;

        mapRot = rotation;

        Log.d(TAG, String.format("MapRot: %1$f", rotation));

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

        Log.d(TAG, String.format("Map Pos: %1$d,%2$d", xy[0], xy[1]));

        mapPos = xy;

        alignMap();
    }
}