package com.ruuvi.station.scanning

import android.content.Context

interface IScanner {
    fun Init(context: Context)

    fun Start()
    fun Stop()
    fun Cleanup()
}