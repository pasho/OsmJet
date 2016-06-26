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
        if (Math.abs(yaw - currentYaw) < 5)
            return;

        currentYaw = yaw;

        Matrix matrix = new Matrix();
        int mapSize = tileSize * 3;
        int mid = mapSize / 2;
        matrix.postRotate(currentYaw, mid, mid);

        int dx = -(mapSize - imageView.getWidth()) / 2;
        int dy = -(mapSize - imageView.getHeight()) / 2;
        matrix.postTranslate(dx, dy);

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

        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        canvas.drawCircle(mapSize / 2, mapSize / 2, 10, paint);

        imageView.setImageBitmap(combo);
    }
}