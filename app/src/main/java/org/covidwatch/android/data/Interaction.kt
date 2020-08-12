package org.covidwatch.android.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.*

@Entity(tableName = "interactions", primaryKeys = ["bytes", "interaction_start"])
@TypeConverters(DateConverter::class)
class Interaction {


    @ColumnInfo(name = "bytes", typeAffinity = ColumnInfo.BLOB)
    var bytes: ByteArray = ByteArray(0)

    /*
    @ColumnInfo(name = "device_id")
    var deviceId: String = ""
     */

    @ColumnInfo(name = "distance_in_feet")
    var distanceInFeet: Double = 100.0

    @ColumnInfo(name = "distance_history")
    var distanceHistory: String = ""

    @ColumnInfo(name = "interaction_start")
    var interactionStart: Date = Date()

    @ColumnInfo(name = "last_seen")
    var lastSeen: Date = Date()

    @ColumnInfo(name = "interaction_end")
    var interactionEnd: Date = Date()

    @ColumnInfo(name = "is_ended")
    var isEnded: Boolean = false

    @ColumnInfo(name = "is_notified")
    var isNotified: Boolean = false

    @ColumnInfo(name = "detected_phone_model")
    var detectedPhoneModel: String = "Unknown"

    @ColumnInfo(name = "interaction_specific_distance")
    var interactionSpecificDistance: Int? = null
}