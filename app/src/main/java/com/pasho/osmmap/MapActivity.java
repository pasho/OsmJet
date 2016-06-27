package com.pasho.osmmap;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.widget.ImageView;

import com.reconinstruments.os.HUDOS;
import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.hardware.sensors.HUDHeadingManager;
import com.reconinstruments.os.hardware.sensors.HeadLocationListener;

public class MapActivity extends Activity implements HeadLocationListener, ITilesConsumer {
    private final String TAG = this.getClass().getSimpleName();
    private final int tileSize = 256;

    private HUDConnectivityManager connectivityManager;
    private HUDHeadingManager headingManager;
    private ImageView imageView = null;
    private LocationManager locationService;
    private TilesManager tileManager;

    private float currentYaw = 0;
    int[] currentTilePos = new int[]{128, 128};

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

    @Override
    public void onHeadLocation(float yaw, float pitch, float roll) {
        if (Math.abs(yaw - currentYaw) < 5)
            return;

        currentYaw = yaw;

        alignMap();
    }

    private void alignMap() {
        Matrix matrix = new Matrix();
        int mapSize = tileSize * 3;

        int dx = -(mapSize - imageView.getWidth()) / 2 - (tileSize / 2 - currentTilePos[0]);
        int dy = -(mapSize - imageView.getHeight()) / 2 - (tileSize / 2 - currentTilePos[1]);

        matrix.setTranslate(dx, dy);
        matrix.postRotate(currentYaw, imageView.getWidth() / 2, imageView.getHeight() / 2);

        imageView.setImageMatrix(matrix);
    }

    @Override
    public void onTiles(Bitmap[] bitmaps) {
        int mapSize = tileSize * 3;
        Bitmap combo = Bitmap.createBitmap(mapSize, mapSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(combo);
        for (int i = 0; i < 9; i++){
            int y = i / 3;
            int x = i - y * 3;

            canvas.drawBitmap(bitmaps[i], x * tileSize, y * tileSize, null);
        }

        imageView.setImageBitmap(combo);
    }

    @Override
    public void onTilePosition(int[] xy) {
        if(Math.abs(currentTilePos[0] - xy[0]) + Math.abs(currentTilePos[1] - xy[1]) < 2)
            return;

        Log.d(TAG, String.format("%1$d,%2$d", xy[0], xy[1]));

        currentTilePos = xy;

        alignMap();
    }
}