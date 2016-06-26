package com.pasho.osmmap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.reconinstruments.os.HUDOS;
import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.hardware.sensors.HUDHeadingManager;
import com.reconinstruments.os.hardware.sensors.HeadLocationListener;

public class MapActivity extends Activity implements HeadLocationListener, ITilesConsumer {
    private final String TAG = this.getClass().getSimpleName();

    private HUDConnectivityManager connectivityManager;
    private HUDHeadingManager headingManager;
    private ImageView imageView = null;
    private LocationManager locationService;
    private TilesManager tileManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.image_layout);

        imageView = (ImageView) findViewById(R.id.imageView);

        connectivityManager = (HUDConnectivityManager) HUDOS.getHUDService(HUDOS.HUD_CONNECTIVITY_SERVICE);
        locationService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        headingManager = (HUDHeadingManager) HUDOS.getHUDService(HUDOS.HUD_HEADING_SERVICE);
        tileManager = new TilesManager(this, connectivityManager);

        System.load("/system/lib/libreconinstruments_jni.so");
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

    float currentYaw = 0;

    @Override
    public void onHeadLocation(float yaw, float pitch, float roll) {
        if (Math.abs(yaw - currentYaw) < 2)
            return;

        currentYaw = yaw;

        Log.d(TAG, String.format(">  %1$f", yaw));

        Matrix matrix = new Matrix();
        int mid = 256 * 3 / 2;
        matrix.postRotate(currentYaw, mid, mid);

        int dx = -(3 * 256 - imageView.getWidth()) / 2;
        int dy = -(3 * 256 - imageView.getHeight()) / 2;
        matrix.postTranslate(dx, dy);

        imageView.setImageMatrix(matrix);
    }

    @Override
    public void onTiles(Bitmap[] bitmaps) {
        Bitmap combo = Bitmap.createBitmap(256 * 3, 256 * 3, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(combo);
        for (int i = 0; i < 9; i++){
            int y = i / 3;
            int x = i - y * 3;

            canvas.drawBitmap(bitmaps[i], x * 256, y * 256, null);
        }

        imageView.setImageBitmap(combo);
    }
}