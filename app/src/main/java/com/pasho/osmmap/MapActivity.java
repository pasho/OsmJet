package com.pasho.osmmap;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import com.reconinstruments.os.HUDOS;
import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.connectivity.http.HUDHttpRequest;
import com.reconinstruments.os.connectivity.http.HUDHttpResponse;
import com.reconinstruments.os.hardware.sensors.HUDHeadingManager;
import com.reconinstruments.os.hardware.sensors.HeadLocationListener;

import java.util.Locale;

public class MapActivity extends Activity implements HeadLocationListener, ITilesConsumer {
    private final String TAG = this.getClass().getSimpleName();
    public static String EXTRA_IMAGE_URL = "IMAGE_URL";

    private HUDConnectivityManager mHUDConnectivityManager;
    private HUDHeadingManager mHUDHeadingManager;
    private ImageView mImage = null;
    private int mDialogRefCnt = 0;
    private ProgressDialog mDialog;
    private LocationManager mLocationService;
    private String tileUrl = "";
    private int[] _currentTile = new int[0];
    private TilesManager tileManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDialog = new ProgressDialog(this);
        mDialog.setMessage("Downloading...");
        setContentView(R.layout.image_layout);

        mImage = (ImageView) findViewById(R.id.imageView);
        mImage.setScaleType(ImageView.ScaleType.MATRIX);

        mHUDConnectivityManager = (HUDConnectivityManager) HUDOS.getHUDService(HUDOS.HUD_CONNECTIVITY_SERVICE);
        mLocationService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mHUDHeadingManager = (HUDHeadingManager) HUDOS.getHUDService(HUDOS.HUD_HEADING_SERVICE);
        tileManager = new TilesManager(this, mHUDConnectivityManager);

        System.load("/system/lib/libreconinstruments_jni.so");
    }

    @Override
    protected void onResume() {
        super.onResume();

        mLocationService.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, tileManager);
        mHUDHeadingManager.register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mLocationService.removeUpdates(tileManager);
        mHUDHeadingManager.unregister(this);
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

        int dx = -(3 * 256 - mImage.getWidth()) / 2;
        int dy = -(3 * 256 - mImage.getHeight()) / 2;
        matrix.postTranslate(dx, dy);

        mImage.setImageMatrix(matrix);
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

        mImage.setImageBitmap(combo);
    }
}