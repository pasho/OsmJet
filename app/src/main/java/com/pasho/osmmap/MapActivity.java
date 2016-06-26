package com.pasho.osmmap;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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

public class MapActivity extends Activity implements LocationListener, HeadLocationListener {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDialog = new ProgressDialog(this);
        mDialog.setMessage("Downloading...");
        setContentView(R.layout.image_layout);
        mImage = (ImageView) findViewById(R.id.imageView);
        mHUDConnectivityManager = (HUDConnectivityManager) HUDOS.getHUDService(HUDOS.HUD_CONNECTIVITY_SERVICE);
        mLocationService = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        mHUDHeadingManager = (HUDHeadingManager) HUDOS.getHUDService(HUDOS.HUD_HEADING_SERVICE);

        System.load("/system/lib/libreconinstruments_jni.so");
    }

    @Override
    protected void onResume() {
        super.onResume();

        mLocationService.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5, this);
        mHUDHeadingManager.register(this);

        if (!tileUrl.isEmpty())
            new DownloadHUDImageTask(mImage).execute(tileUrl);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mLocationService.removeUpdates(this);
    }

    @Override
    public void onLocationChanged(Location location) {

        int zoom = 18;
        double lon = location.getLongitude();
        double lat = location.getLatitude();

        int x = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int y = (int) Math.floor((1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom));

        //http://[abc].tile2.opencyclemap.org/transport/zoom/x/y.png
        String newUrl = String.format(Locale.US, "http://a.tile2.opencyclemap.org/transport/%1$d/%2$d/%3$d.png", zoom, x, y);
        Log.d(TAG, newUrl);

        if (!newUrl.equals(tileUrl)) {
            tileUrl = newUrl;
            new DownloadHUDImageTask(mImage).execute(tileUrl);
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

    float currentYaw = 0;

    @Override
    public void onHeadLocation(float yaw, float pitch, float roll) {
        if (Math.abs(yaw - currentYaw) < 2)
            return;

        currentYaw = yaw;

        Log.d(TAG, String.format(">  %1$f", yaw));

        Matrix matrix = new Matrix();
        matrix.postRotate(currentYaw, 128, 128);
        mImage.setImageMatrix(matrix);
    }

    private class DownloadHUDImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadHUDImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        @Override
        protected void onPreExecute() {
            synchronized (this) {
                if (mDialogRefCnt == 0) {
                    mDialog.show();
                }
                mDialogRefCnt++;
            }
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            String urlDisplay = urls[0];
            Bitmap bitmapImg = null;
            try {
                //Http Get Request
                HUDHttpRequest request = new HUDHttpRequest(HUDHttpRequest.RequestMethod.GET, urlDisplay);
                HUDHttpResponse response = mHUDConnectivityManager.sendWebRequest(request);
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
            bmImage.setImageBitmap(result);

            synchronized (this) {
                if (mDialogRefCnt <= 1) {
                    if (mDialog.isShowing()) {
                        mDialog.dismiss();
                    }
                    mDialogRefCnt = 0;
                } else {
                    mDialogRefCnt--;
                }
            }
        }
    }
}