package org.covidwatch.android

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.covidwatch.android.data.Interaction
import org.covidwatch.android.data.InteractionDAO
import org.covidwatch.android.data.TemporaryContactNumber
import org.covidwatch.android.data.TemporaryContactNumberDAO
import org.covidwatch.android.data.signedreport.SignedReport
import org.covidwatch.android.data.signedreport.SignedReportDAO
import org.covidwatch.android.presentation.MainActivity
import org.covidwatch.android.service.ContactTracerStreams
import org.covidwatch.android.util.NotificationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.tcncoalition.tcnclient.TcnConstants
import org.tcncoalition.tcnclient.TcnKeys
import org.tcncoalition.tcnclient.TcnManager
import org.tcncoalition.tcnclient.crypto.MemoType
import java.lang.Exception
import java.time.LocalDateTime
import java.util.*


class CovidWatchTcnManager(
    private val context: Context,
    private val tcnKeys: TcnKeys,
    private val tcnDao: TemporaryContactNumberDAO,
    private val signedReportDAO: SignedReportDAO,
    private val interactionDAO: InteractionDAO,
    private var foundDeviceModelMap: MutableMap<Int,Any> = mutableMapOf()

) : TcnManager(context) {
    private var _interactionsBeingLogged: Boolean = false
    private val advertisedTcns = mutableListOf<ByteArray>()
    private val TAG = this::class.qualifiedName
    var detectedPhoneModel = "Unknown"

    init {
        val handler = Handler()
        // Define the code block to be executed
        val runnableCode: Runnable = object : Runnable {
            override fun run() {
                if (!_interactionsBeingLogged) {
                    val cal = Calendar.getInstance()
                    val currentDate = Date()

                    cal.time = currentDate
                    cal.add(Calendar.SECOND, -GlobalConstants.INTERACTION_STALE_DURATION_IN_SECONDS)
                    val intrEndDate: Date = cal.time;

                    cal.time = currentDate
                    cal.add(Calendar.DATE, -GlobalConstants.INTERACTION_DELETE_DURATION_IN_DAYS)
                    val intrDelDate: Date = cal.time;

                    GlobalScope.launch(Dispatchers.IO) {
                        interactionDAO.deleteOldInteractions(intrDelDate)
                        interactionDAO.closeOldInteraction(intrEndDate, currentDate)
                    }
                    Log.d("CWTMgr_Handler", "Database Cleaned Up @ $currentDate")
                }

                handler.postDelayed(this, 20000)
            }
        }
        // Start the initial runnable task by posting through the handler
        handler.post(runnableCode)
    }

    override fun foregroundNotification(): NotificationCompat.Builder {
        createNotificationChannelIfNeeded()


        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(
            context,
            GlobalConstants.CHANNEL_ID
        )
            .setContentTitle(context.getString(R.string.foreground_notification_title))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationManagerCompat.IMPORTANCE_HIGH)
            .setCategory(Notification.CATEGORY_SERVICE)
    }

    override fun generateTcn(): ByteArray {
        val tcn = tcnKeys.generateTcn()
        advertisedTcns.add(tcn)
        if (advertisedTcns.size > 65535) {
            advertisedTcns.removeAt(0)
        }
        return tcn
    }

    override fun onTcnFound(tcn: ByteArray, estimatedDistance: Double?, detectedDeviceModelCode : Int) {
        val interactionSpecificDistance : Int? = null
        if (advertisedTcns.contains(tcn)) return
        completeInteraction(tcn, estimatedDistance, detectedDeviceModelCode, interactionSpecificDistance)
    }

    private fun completeInteraction(tcn: ByteArray, estimatedDistance: Double?, detectedDeviceModelCode: Int, interactionSpecificDistance: Int?) {
        var interactionDistance = interactionSpecificDistance
        val detectedDeviceModel = determineDeviceModel(detectedDeviceModelCode)
        try {
            interactionDistance = GlobalConstants.deviceDistanceProfile?.get(detectedDeviceModelCode.toString()).toString().toInt()
            logInteraction(tcn, estimatedDistance, interactionDistance, detectedDeviceModel)
            logTcn(tcn, estimatedDistance)
        } catch (error : Exception) {
            logInteraction(tcn, estimatedDistance, interactionDistance, detectedDeviceModel)
            logTcn(tcn, estimatedDistance)
        }
    }

    private fun determineDeviceModel(detectedDeviceModelCode: Int) : String {
        val iterator: Iterator<*> = TcnConstants.PHONE_MODELS.keys().iterator()
        while (iterator.hasNext()) {
            val key = iterator.next() as String
            if (TcnConstants.PHONE_MODELS.get(key).toString()
                    .toInt() == detectedDeviceModelCode
            ) {
                return key
            }
        }
        return "Unknown"
    }

    /**
     * This notification channel is only required for android versions above
     * android O. This creates the necessary notification channel for the foregroundService
     * to function.
     */
    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                GlobalConstants.CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = ContextCompat.getSystemService(
                context, NotificationManager::class.java
            )
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun bluetoothStateChanged(bluetoothOn: Boolean) {
        val title = if (bluetoothOn) {
            R.string.foreground_notification_title
        } else {
            R.string.foreground_notification_ble_off
        }

        ContextCompat.getSystemService(
            context,
            NotificationManager::class.java
        )?.notify(
            NOTIFICATION_ID,
            foregroundNotification().setContentTitle(context.getString(title)).build()
        )
    }

    fun generateAndUploadReport() {
        // Create a new Signed Report with `uploadState` set to `.notUploaded` and store it in the local persistent store.
        // This will kick off an observer that watches for signed reports which were not uploaded and will upload it.
        val signedReport = SignedReport(tcnKeys.createReport("Hello, World!".toByteArray(),MemoType.CovidWatchV1))
        signedReport.isProcessed = true
        signedReport.uploadState = SignedReport.UploadState.NOTUPLOADED
        GlobalScope.launch(Dispatchers.IO) {
            signedReportDAO.insert(signedReport)
        }
    }

    private fun logTcn(tcnBytes: ByteArray, estimatedDistance: Double?) {
        GlobalScope.launch(Dispatchers.IO) {

            var tcn = tcnDao.findByPrimaryKey(tcnBytes)
            if (tcn == null) {
                tcn = TemporaryContactNumber()
                tcn.bytes = tcnBytes
                if (estimatedDistance != null && estimatedDistance < tcn.closestEstimatedDistanceMeters) {
                    tcn.closestEstimatedDistanceMeters = estimatedDistance
                }
                tcnDao.insert(tcn)
            } else {
                tcn.lastSeenDate = Date()
                if (estimatedDistance != null && estimatedDistance < tcn.closestEstimatedDistanceMeters) {
                    tcn.closestEstimatedDistanceMeters = estimatedDistance
                }
                tcnDao.update(tcn)
            }
        }
    }

    private fun logInteraction(
        tcnBytes: ByteArray,
        estimatedDistance: Double?,
        interactionSpecificDistance: Int?,
        detectedPhoneModel: String
    ) {
        Log.i(TAG, "Interaction specific min contact distance being used $interactionSpecificDistance")
        if (estimatedDistance == null || estimatedDistance > interactionSpecificDistance ?: GlobalConstants.INTERACTION_MIN_DISTANCE_IN_FEET) {
            return
        }

        val cal = Calendar.getInstance()
        val currentDate = Date()

        cal.time = currentDate
        cal.add(Calendar.SECOND, -GlobalConstants.INTERACTION_STALE_DURATION_IN_SECONDS)
        val intrEndDate: Date = cal.time;

        cal.time = currentDate
        cal.add(Calendar.DATE, -21)
        val intrDelDate: Date = cal.time;

        GlobalScope.launch(Dispatchers.IO) {
            _interactionsBeingLogged = true

            //  interactionDAO.deleteOldInteractions(intrDelDate)
            interactionDAO.closeOldInteraction(intrEndDate, currentDate)

            val interactionList = interactionDAO.findLastOpenInteractionsByID(tcnBytes)
            val interaction: Interaction
            val distHistoryList: ArrayList<Double> = ArrayList()
            if (interactionList.isNotEmpty()) {    // new interaction
                interaction = interactionList[0]
                interaction.detectedPhoneModel = detectedPhoneModel
                interaction.interactionSpecificDistance = interactionSpecificDistance
                val strH = interaction.distanceHistory.split(",")
                for (i in 0..strH.size - 1) {
                    // strH[i].toDoubleOrNull()?.let { distHistoryList.add("%.2f".format(it).toDouble()) }
                    strH[i].toDoubleOrNull()?.let { distHistoryList.add(it) }
                }
            } else {
                interaction = Interaction()
                interaction.detectedPhoneModel = detectedPhoneModel
                interaction.interactionSpecificDistance = interactionSpecificDistance
                interaction.bytes = tcnBytes
                interaction.interactionStart = currentDate
                interaction.interactionEnd = intrDelDate
            }

            distHistoryList.add("%.2f".format(estimatedDistance).toDouble())
            while (distHistoryList.size > GlobalConstants.DISTANCE_HISTORY_COUNT) {
                distHistoryList.removeAt(0)
            }

            if (!interaction.isNotified && Date().time - snoozeActivated.time > GlobalConstants.SNOOZE_TIME_IN_SECONDS * 1000) {
                if (currentDate.time - interaction.interactionStart.time >= GlobalConstants.INTERACTION_NOTIFY_WAIT_DURATION_IN_SECONDS * 1000) {
                    NotificationUtils.sendNotificationTooClose(estimatedDistance)
                    interaction.isNotified = true
                }
            }

            interaction.lastSeen = currentDate
            interaction.distanceHistory = distHistoryList.joinToString()

            distHistoryList.sort()

//            var medianDist = estimatedDistance
//            if (distHistoryList.size % 2 == 0) {
//                medianDist = (distHistoryList[distHistoryList.size / 2] + distHistoryList[(distHistoryList.size / 2) - 1]) / 2
//            } else {
//                medianDist = distHistoryList[distHistoryList.size / 2]
//            }
//            interaction.distanceInFeet = medianDist

            interaction.distanceInFeet = distHistoryList.average()

            interactionDAO.insert(interaction)

            _interactionsBeingLogged = false
        }
    }

    companion object {
        var snoozeActivated = Date(0)
    }
}