package com.ruuvi.station.scanning

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import android.os.SystemClock
import java.util.concurrent.CountDownLatch


class ScanWork(context : Context, params : WorkerParameters)
    : Worker(context, params) {

    override fun doWork(): Result {
        val scanner = RuuviTagScanner()
        scanner.Init(applicationContext)
        scanner.Start()
        val countDownLatch = CountDownLatch(1)

        object : Thread() {
            override fun run() {
                SystemClock.sleep(5001L)
                countDownLatch.countDown()
            }
        }.start()

        try {
            countDownLatch.await()
        } catch (ignored: InterruptedException) {
        }
        scanner.Stop()

        return Result.success()
    }

}
