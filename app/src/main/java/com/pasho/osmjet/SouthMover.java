package com.pasho.osmjet;

import android.app.Activity;
import android.location.Location;
import android.location.LocationListener;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Pavel on 21/08/2016.
 */
public class SouthMover {
    private static double R = 6371000;
    private Timer timer = new Timer();
    private Location start;
    private Activity activity;
    private double dLat = 0;

    private final String TAG = this.getClass().getSimpleName();

    public SouthMover(Location start, Activity activity){
        this.start = start;
        this.activity = activity;
    }

    public void register(final LocationListener client) {



        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                activity.runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        dLat += (360 * 10)/(2 * Math.PI * R);
                        Location loc = new Location(start);
                        loc.setLatitude(start.getLatitude() - dLat);
                        loc.setLongitude(start.getLongitude());
                        client.onLocationChanged(loc);
                        Log.i(TAG, "Moved");
                    }
                });
            }
        }, 0, 1000);
    }
}
