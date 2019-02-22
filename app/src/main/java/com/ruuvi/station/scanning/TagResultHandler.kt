package com.ruuvi.station.scanning

import android.content.Context
import android.location.Location
import com.ruuvi.station.gateway.Http
import com.ruuvi.station.model.RuuviTag
import com.ruuvi.station.model.TagSensorReading
import com.ruuvi.station.util.AlarmChecker
import com.ruuvi.station.util.Constants
import java.util.*

class TagResultHandler(val context: Context) {

    fun Save(ruuviTag: RuuviTag, location: Location?) {
        var ruuviTag = ruuviTag
        val dbTag = RuuviTag.get(ruuviTag.id)
        if (dbTag != null) {
            ruuviTag = dbTag.preserveData(ruuviTag)
            ruuviTag.update()
            if (!dbTag.favorite) return
        } else {
            ruuviTag.updateAt = Date()
            ruuviTag.save()
            return
        }

        Http.post(ruuviTag, location, context)
        saveReading(ruuviTag)
        removeOldReadings()
    }

    private var lastLogged: MutableMap<String, Long> = HashMap()
    fun saveReading(ruuviTag: RuuviTag) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, -Constants.DATA_LOG_INTERVAL)
        val loggingThreshold = calendar.time.time
        for (entry in lastLogged.entries) {
            if (entry.key == ruuviTag.id && entry.value > loggingThreshold) {
                return
            }
        }

        lastLogged.put(ruuviTag.id, Date().time)
        val reading = TagSensorReading(ruuviTag)
        reading.save()
        AlarmChecker.check(ruuviTag, context)
    }

    private var lastRemoved: Long = 0
    fun removeOldReadings() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.SECOND, -Constants.DATA_LOG_INTERVAL)
        if (lastRemoved > calendar.time.time) {
            return
        }

        lastRemoved = Date().time
        TagSensorReading.removeOlderThan(24)
    }
}