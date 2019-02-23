package com.ruuvi.station;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.raizlabs.android.dbflow.config.FlowManager;
import com.ruuvi.station.scanning.AltBeaconScanner;
import com.ruuvi.station.scanning.IScanner;
import com.ruuvi.station.scanning.RuuviTagScanner;
import com.ruuvi.station.service.ScannerJobService;
import com.ruuvi.station.util.BackgroundScanModes;
import com.ruuvi.station.util.Foreground;
import com.ruuvi.station.util.Preferences;
import com.ruuvi.station.util.ServiceUtils;

import org.altbeacon.bluetooth.BluetoothMedic;

/**
 * Created by io53 on 10/09/17.
 */

public class RuuviScannerApplication extends Application {
    private static final String TAG = "RuuviScannerApplication";
    boolean running = false;
    private Preferences prefs;
    private boolean foreground = false;
    BluetoothMedic medic;
    RuuviScannerApplication me;
    IScanner scanner;

    public void stopScanning() {
        Log.d(TAG, "Stopping scanning");
        running = false;
        scanner.Stop();
    }

    public void disposeStuff() {
        Log.d(TAG, "Stopping scanning");
        medic = null;
        running = false;
        scanner.Stop();
        scanner.Cleanup();
    }

    private void stopBackgroundScanning() {
        JobScheduler scheduler = (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
        if (scheduler != null) scheduler.cancelAll();
    }

    private boolean runForegroundIfEnabled() {
        ServiceUtils su = new ServiceUtils(getApplicationContext());
        if (prefs.getBackgroundScanMode() == BackgroundScanModes.FOREGROUND || prefs.getBackgroundScanMode() == BackgroundScanModes.GATEWAY) {
            disposeStuff();
            su.startForegroundService(prefs.getBackgroundScanMode());
            return true;
        }
        su.stopForegroundService();
        su.stopGatewayService();
        return false;
    }

    public void startForegroundScanning() {
        if (runForegroundIfEnabled()) return;

        boolean altBeaconMode = prefs.getUseAltBeacon();
        if (scanner instanceof AltBeaconScanner && !altBeaconMode || scanner instanceof RuuviTagScanner && altBeaconMode) {
            scanner.Stop();
            scanner.Cleanup();
            if (altBeaconMode) scanner = new AltBeaconScanner();
            else scanner = new RuuviTagScanner();
            scanner.Init(me);
            foreground = false;
        }

        if (foreground) return;
        foreground = true;

        Toast.makeText(me, "AltBeacon scanner = " + (altBeaconMode ? "YES" : "NO"), Toast.LENGTH_SHORT).show();
        scanner.Start();
    }

    public void startBackgroundScanning() {
        Log.d(TAG, "Starting background scanning");
        if (runForegroundIfEnabled()) return;
        if (prefs.getBackgroundScanMode() != BackgroundScanModes.BACKGROUND) {
            Log.d(TAG, "Background scanning is not enabled, ignoring");
            return;
        }
        if (prefs.getUseAltBeacon()) {
            ((AltBeaconScanner)scanner).StartBackground();
        } else {
            int scanInterval = new Preferences(getApplicationContext()).getBackgroundScanInterval() * 1000;
            int minInterval = 15 * 60 * 1000;
            if (scanInterval < minInterval) scanInterval = minInterval;
            ComponentName componentName = new ComponentName(this, ScannerJobService.class);
            JobInfo info = new JobInfo.Builder(1, componentName)
                    .setPersisted(true)
                    .setPeriodic(scanInterval)
                    .build();
            JobScheduler scheduler = (JobScheduler)getSystemService(JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.schedule(info);
                Log.d(TAG, "Scheduling bg scan job");
            }
        }
        //if (medic == null) medic = setupMedic(getApplicationContext());
    }

    public static BluetoothMedic setupMedic(Context context) {
        BluetoothMedic medic = BluetoothMedic.getInstance();
        medic.enablePowerCycleOnFailures(context);
        medic.enablePeriodicTests(context, BluetoothMedic.SCAN_TEST);
        return medic;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        me = this;
        Log.d(TAG, "App class onCreate");
        FlowManager.init(getApplicationContext());
        prefs = new Preferences(getApplicationContext());
        if (prefs.getUseAltBeacon()) {
            scanner = new AltBeaconScanner();
        } else {
            scanner = new RuuviTagScanner();
        }
        scanner.Init(getApplicationContext());
        Foreground.init(this);
        Foreground.get().addListener(listener);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!foreground) {
                    if (prefs.getBackgroundScanMode() == BackgroundScanModes.FOREGROUND || prefs.getBackgroundScanMode() == BackgroundScanModes.GATEWAY) {
                        new ServiceUtils(getApplicationContext()).startForegroundService(prefs.getBackgroundScanMode());
                    } else if (prefs.getBackgroundScanMode() == BackgroundScanModes.BACKGROUND) {
                        startBackgroundScanning();
                    }
                }
            }
        }, 5000);
    }

    Foreground.Listener listener = new Foreground.Listener() {
        public void onBecameForeground() {
            Log.d(TAG, "onBecameForeground");
            startForegroundScanning();
        }

        public void onBecameBackground() {
            Log.d(TAG, "onBecameBackground");
            foreground = false;
            ServiceUtils su = new ServiceUtils(getApplicationContext());
            if (prefs.getBackgroundScanMode() == BackgroundScanModes.DISABLED) {
                // background scanning is disabled so all scanning things will be killed
                stopScanning();
                su.stopForegroundService();
                su.stopGatewayService();
                stopBackgroundScanning();
            } else if (prefs.getBackgroundScanMode() == BackgroundScanModes.BACKGROUND) {
                su.stopForegroundService();
                su.stopGatewayService();
                stopScanning();
                startBackgroundScanning();
            } else {
                stopBackgroundScanning();
                disposeStuff();
                su.startForegroundService(prefs.getBackgroundScanMode());
            }
            //if (ruuviRangeNotifier != null) ruuviRangeNotifier.gatewayOn = true;
        }
    };
}
