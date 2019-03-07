package com.ruuvi.station.scanning;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;

import com.ruuvi.station.util.Constants;
import com.ruuvi.station.util.Preferences;
import com.ruuvi.station.util.Utils;

import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.Region;
import org.jetbrains.annotations.NotNull;

public class AltBeaconScanner implements BeaconConsumer, IScanner {
    private static final String TAG = "AltBeaconIScanner";
    private BeaconManager beaconManager;
    private Region region;
    private RuuviRangeNotifier ruuviRangeNotifier;
    private boolean  running;
    private Context context;

    @Override
    public void Init(@NotNull Context context) {
        this.context = context;
        ruuviRangeNotifier = new RuuviRangeNotifier(context, "AltBeaconIScanner");
        region = new Region("com.ruuvi.station.leRegion", null, null, null);
    }

    public void Start() {
        Utils.removeStateFile(context);
        Log.d(TAG, "Starting scanning");
        bindBeaconManager(context);
        beaconManager.setBackgroundMode(false);
    }

    public void StartBackground() {
        bindBeaconManager(getApplicationContext());
        int scanInterval = new Preferences(getApplicationContext()).getBackgroundScanInterval() * 1000;
        int minInterval = 15 * 60 * 1000;
        if (scanInterval < minInterval) scanInterval = minInterval;
        if (scanInterval != beaconManager.getBackgroundBetweenScanPeriod()) {
            beaconManager.setBackgroundBetweenScanPeriod(scanInterval);
            try {
                beaconManager.updateScanPeriods();
            } catch (Exception e) {
                Log.e(TAG, "Could not update scan intervals");
            }
        }
        beaconManager.setBackgroundMode(true);
        if (ruuviRangeNotifier != null) ruuviRangeNotifier.gatewayOn = true;
        //if (medic == null) medic = setupMedic(getApplicationContext());
    }

    public void Stop() {
        Log.d(TAG, "Stopping scanning");
        running = false;
        try {
            if (beaconManager != null) beaconManager.stopRangingBeaconsInRegion(region);
        } catch (Exception e) {
            Log.d(TAG, "Could not remove ranging region");
        }
    }


    public void Cleanup() {
        if (beaconManager == null) return;
        running = false;
        beaconManager.removeRangeNotifier(ruuviRangeNotifier);
        try {
            beaconManager.stopRangingBeaconsInRegion(region);
        } catch (Exception e) {
            Log.d(TAG, "Could not remove ranging region");
        }
        beaconManager.unbind(this);
        beaconManager = null;
    }

    private void bindBeaconManager(Context context) {
        if (beaconManager == null) {
            beaconManager = BeaconManager.getInstanceForApplication(context.getApplicationContext());
            Utils.setAltBeaconParsers(beaconManager);
            beaconManager.setBackgroundScanPeriod(5000);
            beaconManager.bind(this);
        } else if (!running) {
            running = true;
            try {
                beaconManager.startRangingBeaconsInRegion(region);
            } catch (Exception e) {
                Log.d(TAG, "Could not start ranging again");
            }
        }
    }


    @Override
    public void onBeaconServiceConnect() {
        Log.d(TAG, "onBeaconServiceConnect");
        if (!beaconManager.getRangingNotifiers().contains(ruuviRangeNotifier)) {
            beaconManager.addRangeNotifier(ruuviRangeNotifier);
        }
        running = true;
        try {
            beaconManager.startRangingBeaconsInRegion(region);
        } catch (Exception e) {
            Log.e(TAG, "Could not start ranging");
        }
    }

    @Override
    public Context getApplicationContext() {
        return context;
    }

    @Override
    public void unbindService(ServiceConnection serviceConnection) {
    }

    @Override
    public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
        return false;
    }

}
