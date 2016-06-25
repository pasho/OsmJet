package com.reconinstruments.connectivitydemo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.reconinstruments.os.HUDOS;
import com.reconinstruments.os.connectivity.HUDConnectivityManager;
import com.reconinstruments.os.connectivity.IHUDConnectivity;

public class MainActivity extends Activity implements View.OnClickListener, IHUDConnectivity
{
    private static final String TAG = "MainActivity";
    private HUDConnectivityManager mHUDConnectivityManager = null;

    private TextView mConnectDevice;
    private TextView mHUDConnected;
    private TextView mLocalWeb;
    private TextView mRemoteWeb;
    private Button mDownloadFile;
    private Button mUploadFile;
    private Button mDownloadImage;

    private final int COL_RED    = 0xAAFF0000;
    private final int COL_ORANGE = 0xAAFF6600;
    private final int COL_GREEN  = 0xAA00FF00;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get view references (from activity_main.xml)
        mConnectDevice = (TextView) findViewById(R.id.connected_device);
        mHUDConnected  = (TextView) findViewById(R.id.hud_connected);
        mLocalWeb      = (TextView) findViewById(R.id.local_web_connected);
        mRemoteWeb     = (TextView) findViewById(R.id.remote_web_connected);

        mDownloadFile  = (Button) findViewById(R.id.download_file_button);
        mUploadFile    = (Button) findViewById(R.id.upload_file_button);
        mDownloadImage = (Button) findViewById(R.id.load_image_button);

        mDownloadFile.setOnClickListener(this);
        mUploadFile.setOnClickListener(this);
        mDownloadImage.setOnClickListener(this);

        mDownloadFile.setEnabled(false);
        mUploadFile.setEnabled(false);
        mDownloadImage.setEnabled(false);

        //Get an instance of HUDConnectivityManager
        mHUDConnectivityManager = (HUDConnectivityManager) HUDOS.getHUDService(HUDOS.HUD_CONNECTIVITY_SERVICE);
        if(mHUDConnectivityManager == null)
        {
            Log.e(TAG, "Failed to get HUDConnectivityManager");
            finish();
        }

        setViewColor(mHUDConnected, COL_RED);
        setViewColor(mLocalWeb, COL_RED);
        setViewColor(mRemoteWeb, COL_RED);
        if(mHUDConnectivityManager.isHUDConnected())
        {
            Log.d(TAG, "HUD is connected.");
            mHUDConnected.setText("HUD Connected");
        }
        else
        {
            Log.d(TAG, "HUD is disconnected.");
            mHUDConnected.setText("HUD Disconnected");
        }

        // !!!! -------- IMPORTANT NOTICE -------- !!!! //
        //Note: This following line is necessary for HUDConnectivityManager to run properly
        System.load("/system/lib/libreconinstruments_jni.so");
        // !!!! -------- IMPORTANT NOTICE -------- !!!! //
    }

    @Override
    public void onResume()
    {
        super.onResume();
        // Register the IHUDConnectivity to HUDConnectivityManager
        mHUDConnectivityManager.register(this);
    }

    @Override
    public void onPause()
    {
        // Unregister the IHUDConnectivity from HUDConnectivityManager
        mHUDConnectivityManager.unregister(this);
        super.onPause();
    }

    @Override
    public void onClick(View v)
    {
        Intent intent = null;
        if (v == mDownloadImage)
        {
            intent = new Intent(this, ImageDownloadActivity.class);
            intent.putExtra(ImageDownloadActivity.EXTRA_IMAGE_URL, "http://www.reconinstruments.com/wp-content/themes/recon/img/jet/slide-3.jpg");
            startActivity(intent);
        }
        else if (v == mDownloadFile)
        {
            intent = new Intent(this, FileDownloadActivity.class);
            startActivity(intent);
        }
        else if (v == mUploadFile)
        {
            intent = new Intent(this, FileUploadActivity.class);
            startActivity(intent);
        }
    }

    @Override
    public void onConnectionStateChanged(ConnectionState state)
    {
        Log.d(TAG, "onConnectionStateChanged : state:" + state);
        switch (state) {
            case LISTENING:
                mHUDConnected.setText("HUD Listening");
                setViewColor(mHUDConnected, COL_RED);
                break;
            case CONNECTED:
                mHUDConnected.setText("Phone Connected");
                setViewColor(mHUDConnected, COL_GREEN);
                break;
            case CONNECTING:
                mHUDConnected.setText("Phone Connecting");
                setViewColor(mHUDConnected, COL_ORANGE);
                break;
            case DISCONNECTED:
                mHUDConnected.setText("Phone Disconnected");
                setViewColor(mHUDConnected, COL_RED);
                break;
            default:
                Log.e(TAG,"onConnectionStateChanged() with unknown state:" + state);
                break;
        }
    }

    @Override
    public void onNetworkEvent(NetworkEvent networkEvent, boolean hasNetworkAccess)
    {
        switch (networkEvent) {
            case LOCAL_WEB_GAINED:
                mLocalWeb.setText("Local Web: ON");
                setViewColor(mLocalWeb, COL_GREEN);
                break;
            case LOCAL_WEB_LOST:
                mLocalWeb.setText("Local Web: OFF");
                setViewColor(mLocalWeb, COL_RED);
                break;
            case REMOTE_WEB_GAINED:
                mRemoteWeb.setText("Remote Web: ON");
                setViewColor(mRemoteWeb, COL_GREEN);
                break;
            case REMOTE_WEB_LOST:
                mRemoteWeb.setText("Remote Web: OFF");
                setViewColor(mRemoteWeb, COL_RED);
                break;
            default:
                Log.e(TAG,"onNetworkEvent() with unknown networkEvent:" + networkEvent);
                break;
        }

        Log.d(TAG, "onNetworkEvent() - networkEvent: " + networkEvent + ", hasNetworkAccess: " + hasNetworkAccess);
        Log.d(TAG, "onNetworkEvent() - getDeviceName(): " + mHUDConnectivityManager.getDeviceName() +
                ", isHUDConnected(): " + mHUDConnectivityManager.isHUDConnected() +
                ", hasLocalWeb(): " + mHUDConnectivityManager.hasLocalWeb() +
                ", hasRemoteWeb(): " + mHUDConnectivityManager.hasRemoteWeb() +
                ", hasWebConnection(): " + mHUDConnectivityManager.hasWebConnection() +
                ", getConnectionState(): " + mHUDConnectivityManager.getConnectionState());

        mDownloadFile.setEnabled(hasNetworkAccess);
        mUploadFile.setEnabled(hasNetworkAccess);
        mDownloadImage.setEnabled(hasNetworkAccess);
    }

    @Override
    public void onDeviceName(String deviceName)
    {
        Log.d(TAG,"onDeviceName(deviceName: " + deviceName + ")");
        if(deviceName != null && deviceName.length() > 0)
        {
            mConnectDevice.setText(deviceName);
            setViewColor(mConnectDevice, COL_GREEN);
        }
        else
        {
            mConnectDevice.setText("<NO DEVICE>");
            setViewColor(mConnectDevice, COL_RED);
        }
    }

    private void setViewColor(View view, int color)
    {
        view.setBackgroundColor(color);
    }
}
