package com.ruuvi.station.scanning;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.ruuvi.station.gateway.Http;
import com.ruuvi.station.model.LeScanResult;
import com.ruuvi.station.model.RuuviTag;
import com.ruuvi.station.model.TagSensorReading;
import com.ruuvi.station.util.AlarmChecker;
import com.ruuvi.station.util.Constants;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuuviRangeNotifier implements RangeNotifier {
    private static final String TAG = "RuuviRangeNotifier";
    private String from;
    private Context context;
    private Location tagLocation;

    public boolean gatewayOn = false;
    private FusedLocationProviderClient mFusedLocationClient;
    private TagResultHandler resultHandler;

    private long last = 0;

    public RuuviRangeNotifier(Context context, String from) {
        Log.d(TAG, "Setting up range notifier from " + from);
        this.context = context;
        this.from = from;
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        resultHandler = new TagResultHandler(context);
    }

    private void updateLocation() {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    tagLocation = location;
                }
            });
        }
    }

    @Override
    public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
        long now = System.currentTimeMillis();
        if (now <= last + 500) {
            Log.d(TAG, "Double range bug");
            return;
        }
        last = now;
        if (gatewayOn) updateLocation();
        List<RuuviTag> tags = new ArrayList<>();
        Log.d(TAG, from + " " + " found " + beacons.size());
        foundBeacon: for (Beacon beacon : beacons) {
            // the same tag can appear multiple times
            for (RuuviTag tag : tags) {
                if (tag.id.equals(beacon.getBluetoothAddress())) continue foundBeacon;
            }
            RuuviTag tag = LeScanResult.fromAltbeacon(beacon);
            if (tag != null) {
                resultHandler.Save(tag, tagLocation);
                tags.add(tag);
            }
        }
        //if (tags.size() > 0 && gatewayOn) Http.post(tags, tagLocation, context);
    }
}
