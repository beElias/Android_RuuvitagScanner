package com.ruuvi.station.scanning;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.util.Log;

import com.ruuvi.station.model.LeScanResult;
import com.ruuvi.station.model.RuuviTag;
import com.ruuvi.station.model.TagSensorReading;
import com.ruuvi.station.service.GatewayService;
import com.ruuvi.station.util.AlarmChecker;
import com.ruuvi.station.util.Constants;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuuviTagScanner implements IScanner {
    private static final String TAG = "RuuviTagScanner";
    private RuuviTagListener listener;

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private ScanSettings scanSettings;
    private BluetoothLeScanner scanner;
    private List<ScanFilter> scanFilters = new ArrayList<>();
    private TagResultHandler resultHandler;
    private boolean scanning = false;

    @Override
    public void Init(@NotNull Context context) {
        this.context = context;

        resultHandler = new TagResultHandler(context);
        ScanFilter.Builder builder = new ScanFilter.Builder();
        scanFilters.add(builder.build());

        scanSettings = new ScanSettings.Builder()
                .setReportDelay(0)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        scanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    public void setListener(RuuviTagListener listener) {
        this.listener = listener;
    }

    @Override
    public void Start() {

        if (scanning || !canScan()) return;
        scanning = true;
        scanner.startScan(scanFilters, scanSettings, nsCallback);
    }

    @Override
    public void Stop() {

        if (!canScan()) return;
        scanning = false;
        scanner.stopScan(nsCallback);
    }

    @Override
    public void Cleanup() {

    }
    /*
    public RuuviTagScanner(RuuviTagListener listener, Context context) {


    }
    */

    public void start() {
    }

    public void stop() {
    }

    private ScanCallback nsCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            foundDevice(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
        }
    };

    private void foundDevice(BluetoothDevice device, int rssi, byte[] data) {
        LeScanResult dev = new LeScanResult();
        dev.device = device;
        dev.rssi = rssi;
        dev.scanData = data;

        Log.d(TAG, "found: " + device.getAddress());
        RuuviTag tag = dev.parse();
        if (tag != null) {
            if (listener != null) listener.tagFound(tag);
            resultHandler.Save(tag, null);
        }
    }

    private boolean canScan() {
        return scanner != null;
    }
}
