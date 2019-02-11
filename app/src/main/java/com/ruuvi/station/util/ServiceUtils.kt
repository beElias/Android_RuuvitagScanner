package com.ruuvi.station.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ruuvi.station.service.AltBeaconScannerForegroundService
import com.ruuvi.station.service.AltBeaconScannerService
import com.ruuvi.station.service.ScannerService

class ServiceUtils(val context: Context) {
    fun stopService(): ServiceUtils {
        val scannerService = Intent(context, AltBeaconScannerService::class.java)
        context.stopService(scannerService)
        return this
    }

    fun startService(): ServiceUtils {
        if (!isRunning(AltBeaconScannerService::class.java)) {
            val scannerService = Intent(context, AltBeaconScannerService::class.java)
            context.startService(scannerService)
        }
        return this
    }

    fun stopGatewayService(): ServiceUtils {
        val scannerService = Intent(context, ScannerService::class.java)
        context.stopService(scannerService)
        return this
    }

    fun stopForegroundService(): ServiceUtils {
        val scannerService = Intent(context, AltBeaconScannerForegroundService::class.java)
        context.stopService(scannerService)
        return this
    }

    fun startForegroundService(scanMode: BackgroundScanModes): ServiceUtils {
        if (scanMode == BackgroundScanModes.FOREGROUND) {
            if (!isRunning(AltBeaconScannerForegroundService::class.java)) {
                stopGatewayService()
                val scannerService = Intent(context, AltBeaconScannerForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(scannerService)
                } else {
                    context.startService(scannerService)
                }
            }
        } else if (scanMode == BackgroundScanModes.GATEWAY) {
            if (!isRunning(ScannerService::class.java)) {
                stopForegroundService()
                val scannerService = Intent(context, ScannerService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(scannerService)
                } else {
                    context.startService(scannerService)
                }
            }
        }
        return this
    }

    fun forceStartIfRunningForegroundService(): ServiceUtils {
        if (isRunning(AltBeaconScannerForegroundService::class.java)) {
            val scannerService = Intent(context, AltBeaconScannerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(scannerService)
            } else {
                context.startService(scannerService)
            }
        }
        return this
    }

    fun isRunning(serviceClass: Class<*>): Boolean {
        val mgr = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in mgr!!.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}