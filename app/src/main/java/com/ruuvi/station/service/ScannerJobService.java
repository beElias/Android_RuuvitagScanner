package com.ruuvi.station.service;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.ruuvi.station.scanning.RuuviTagScanner;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ScannerJobService extends JobService {
    private static final String TAG = "ScannerJobService";
    public static final int REQUEST_CODE = 9001;
    private static final int SCAN_TIME_MS = 5000;

    //private PowerManager.WakeLock wakeLock;

    private JobParameters jobParameters;
    private RuuviTagScanner scanner;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        Log.d(TAG, "Woke up");
        this.jobParameters = jobParameters;

        scanner = new RuuviTagScanner();
        scanner.Init(getApplicationContext());

        FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
        if (ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mFusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    scanner.location = location;
                }
            });
        }

        scanner.Start();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                scanner.Stop();
            }
        }, SCAN_TIME_MS);

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }
}
