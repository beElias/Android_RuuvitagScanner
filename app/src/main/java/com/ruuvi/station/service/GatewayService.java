package com.ruuvi.station.service;

import android.app.ActivityManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import com.ruuvi.station.R;
import com.ruuvi.station.feature.StartupActivity;
import com.ruuvi.station.model.TagSensorReading;
import com.ruuvi.station.scanning.RuuviTagScanner;
import com.ruuvi.station.util.BackgroundScanModes;
import com.ruuvi.station.util.Foreground;
import com.ruuvi.station.util.Preferences;

public class GatewayService extends Service {
    private static final String TAG = "GatewayService";

    private boolean scanning;
    private Handler handler;
    private boolean isForegroundMode = false;
    private boolean foreground = false;
    private RuuviTagScanner scanner;
    private PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Foreground.init(getApplication());
        Foreground.get().addListener(listener);

        foreground = true;
        scanner = new RuuviTagScanner();
        scanner.Init(this);

        startFG();
        handler = new Handler();
        handler.post(starter);
    }

    private void updateLocation() {
        FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    scanner.location = location;
                }
            });
        }
    }

    private boolean getForegroundMode() {
        Preferences prefs = new Preferences(this);
        return prefs.getBackgroundScanMode() == BackgroundScanModes.GATEWAY;
    }

    private Runnable reStarter = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Restarting scanner");
            stopScan();
            TagSensorReading.removeOlderThan(24);
            handler.postDelayed(starter, 1000);
        }
    };

    private Runnable starter = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Restarting scanner");
            updateLocation();
            handler.postDelayed(reStarter, 5 * 60 * 1000);
            startScan();
        }
    };

    public void startFG() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "foreground_scanner_channel";
        if (Build.VERSION.SDK_INT >= 26) {
            CharSequence channelName = "RuuviStation foreground scanner";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, importance);
            try {
                notificationManager.createNotificationChannel(notificationChannel);
            } catch (Exception e) {
                Log.e(TAG, "Could not create notification channel");
            }
        }

        isForegroundMode = true;
        Intent notificationIntent = new Intent(this, StartupActivity.class);

        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.ic_launcher);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        NotificationCompat.Builder notification;
        notification
                = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setContentTitle(this.getString(R.string.gateway_notification_title))
                .setSmallIcon(R.mipmap.ic_launcher_small)
                .setTicker(this.getString(R.string.scanner_notification_ticker))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(this.getString(R.string.scanner_notification_message)))
                .setContentText(this.getString(R.string.scanner_notification_message))
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                .setLargeIcon(bitmap)
                .setContentIntent(pendingIntent);

        notification.setSmallIcon(R.drawable.ic_ruuvi_notification_icon_v1);

        startForeground(1337, notification.build());
    }

    private boolean canScan() {
        return scanner != null;
    }

    public void startScan() {
        if (scanning || !canScan()) return;
        scanning = true;
        scanner.Start();
    }

    public void stopScan() {
        if (!canScan()) return;
        scanning = false;
        scanner.Stop();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "DESTROYED");
        stopScan();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    Foreground.Listener listener = new Foreground.Listener() {
        public void onBecameForeground() {
            if (wakeLock != null) {
                try {
                    wakeLock.release();
                    Log.d(TAG, "Released wakelock");
                } catch (Exception e) {
                    Log.e(TAG, "Could not release wakelock");
                }
            }
            foreground = true;
            if (!isRunning(GatewayService.class))
                startService(new Intent(GatewayService.this, GatewayService.class));
        }

        public void onBecameBackground() {
            foreground = false;
            if (!getForegroundMode()) {
                handler.removeCallbacksAndMessages(null);
                stopSelf();
                isForegroundMode = false;
            } else {
                PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(POWER_SERVICE);
                try {
                    wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "ruuviStation:serviceWakelock");
                    wakeLock.acquire();
                    Log.d(TAG, "Acquired wakelock");
                } catch (Exception e) {
                    Log.e(TAG, "Could not acquire wakelock");
                }
                if (!isForegroundMode) startFG();
            }
        }
    };

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopForeground(true);
        stopSelf();
        Foreground.get().removeListener(listener);
    }

    private boolean isRunning(Class<?> serviceClass) {
        ActivityManager mgr = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : mgr.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
