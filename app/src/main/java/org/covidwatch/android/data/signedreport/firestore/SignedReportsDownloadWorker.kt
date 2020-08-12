package org.covidwatch.android.data.signedreport.firestore

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import cafe.cryptography.ed25519.Ed25519PublicKey
import cafe.cryptography.ed25519.Ed25519Signature
import org.covidwatch.android.GlobalConstants
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import org.covidwatch.android.R
import org.covidwatch.android.data.CovidWatchDatabase
import org.covidwatch.android.data.TemporaryContactNumberDAO
import org.covidwatch.android.model.ContactRegistration
import org.covidwatch.android.service.ContactTracerStreams
import org.covidwatch.android.util.NotificationUtils
import org.json.JSONObject
import org.tcncoalition.tcnclient.TcnConstants
import org.tcncoalition.tcnclient.crypto.KeyIndex
import org.tcncoalition.tcnclient.crypto.MemoType
import org.tcncoalition.tcnclient.crypto.Report
import org.tcncoalition.tcnclient.crypto.SignedReport
import java.text.SimpleDateFormat
import java.util.*

class SignedReportsDownloadWorker(var context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    private var result = Result.failure()

    override fun doWork(): Result {

        Log.i(TAG, "Downloading signed reports and updating phone models...")

        //update phone models info from server
        updatePhoneModels()

        //update device distance profiles from server
        updateDeviceDistanceProfile()

        val now = Date()

        val lastFetchTime = FirestoreConstants.lastFetchTime()
        var fetchSinceTime = context.getSharedPreferences(
            context.getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        ).getLong(
            context.getString(R.string.preference_last_temporary_contact_numbers_download_date),
            lastFetchTime.time
        )
        if (fetchSinceTime < lastFetchTime.time) {
            fetchSinceTime = lastFetchTime.time
        }

        val instance = FirebaseFirestore.getInstance()

        val task =
            FirebaseFirestore.getInstance().collection(FirestoreConstants.COLLECTION_SIGNED_REPORTS)
                .whereGreaterThan(
                    FirestoreConstants.FIELD_TIMESTAMP,
                    Timestamp(Date(fetchSinceTime))
                )
                .get()
                .continueWith(
                    CovidWatchDatabase.databaseWriteExecutor,
                    Continuation<QuerySnapshot, Result> { task ->
                        //result = Result.success()
                        val queryDocumentSnapshots = task.result
                        if (queryDocumentSnapshots != null) {
                            Log.i(
                                TAG,
                                "Downloaded ${queryDocumentSnapshots.size()} signed report(s)"
                            )
                            result = try {
                                queryDocumentSnapshots.documentChanges.filter {
                                    it.type == DocumentChange.Type.ADDED
                                }
                                markLocalTemporaryContactNumbers(
                                    queryDocumentSnapshots.documentChanges,
                                    true
                                )

                                with(
                                    context.getSharedPreferences(
                                        context.getString(R.string.preference_file_key),
                                        Context.MODE_PRIVATE
                                    ).edit()
                                ) {
                                    putLong(
                                        context.getString(R.string.preference_last_temporary_contact_numbers_download_date),
                                        now.time
                                    )
                                    commit()
                                }

                                Result.success()

                            } catch (exception: Exception) {
                                Result.failure()
                            }
                        } else {
                            result = Result.failure()
                        }
                        null
                    })

        Tasks.await(task)

        Log.i(TAG, "Finished download task")

        return result
    }

    @SuppressLint("CheckResult")
    private fun updatePhoneModels() {
        ContactTracerStreams().getPhoneModels()?.subscribe({
            val responseData = it?.string()
            TcnConstants.PHONE_MODELS = JSONObject(responseData!!)
            Log.i(TAG, "Successfully updated phone models from server.")
        }){
            Log.i(TAG, it.message + " Failed to download phone models from server.")
        }
    }

    @SuppressLint("CheckResult")
    private fun updateDeviceDistanceProfile() {
        val deviceId : Int = if (TcnConstants.PHONE_MODELS.has(Build.MODEL)) TcnConstants.PHONE_MODELS[Build.MODEL].toString().toInt() else 0
        ContactTracerStreams().getPhoneDistanceProfile(deviceId)?.subscribe({
            val responseData = it?.string()
            GlobalConstants.deviceDistanceProfile = JSONObject(responseData!!)
        }){
            Log.e(TAG, it.message!! + " Failed download distance profile from server")
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun markLocalTemporaryContactNumbers(
        documentChanges: List<DocumentChange>,
        wasPotentiallyInfectious: Boolean
    ) {
        if (documentChanges.isEmpty()) return

        val temporaryContactNumberDAO: TemporaryContactNumberDAO =
            CovidWatchDatabase.getInstance(context).temporaryContactNumberDAO()

        documentChanges.forEach { documentChange ->
            val data = documentChange.document
            val temporaryContactKeyBytes =
                (data[FirestoreConstants.FIELD_TEMPORARY_CONTACT_KEY_BYTES] as? Blob)?.toBytes()
                    ?: return@forEach
            val endIndex =
                (data[FirestoreConstants.FIELD_END_INDEX] as? Long)?.toShort() ?: return@forEach
            val memoData =
                (data[FirestoreConstants.FIELD_MEMO_DATA] as? Blob)?.toBytes() ?: return@forEach
            val memoType =
                (data[FirestoreConstants.FIELD_MEMO_TYPE] as? Long)?.toShort() ?: return@forEach
            val reportVerificationPublicKeyBytes =
                (data[FirestoreConstants.FIELD_REPORT_VERIFICATION_PUBLIC_KEY_BYTES] as? Blob)?.toBytes()
                    ?: return@forEach
            val signatureBytes =
                (data[FirestoreConstants.FIELD_SIGNATURE_BYTES] as? Blob)?.toBytes()
                    ?: return@forEach
            val startIndex =
                (data[FirestoreConstants.FIELD_START_INDEX] as? Long)?.toShort() ?: return@forEach

            val report = Report(
                Ed25519PublicKey.fromByteArray(reportVerificationPublicKeyBytes),
                temporaryContactKeyBytes,
                KeyIndex(startIndex),
                KeyIndex(endIndex),
                MemoType.fromByteArray(arrayOf(memoType.toByte()).toByteArray()),
                memoData
            )

            val signedReport = SignedReport(report, Ed25519Signature.fromByteArray(signatureBytes))
            val signatureBase64EncodedString = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

            try {
                signedReport.verify()
                Log.i(
                    TAG,
                    "Source integrity verification for signed report ($signatureBase64EncodedString) succeeded"
                )
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Source integrity verification for signed report ($signatureBase64EncodedString) failed"
                )
                return@forEach
            }

            val recomputedTemporaryContactNumbers = report.temporaryContactNumbers
            val identifiers = mutableListOf<ByteArray>()
            recomputedTemporaryContactNumbers.forEach {
                Log.i(TAG,"TCN Marked for Potential Infectious: ${it}")
                val string = String(it.bytes)
               // Log.i(TAG,"TCN Byte to String: ${string}  -- Base64: ${Base64.encodeToString(it.bytes,Base64.NO_WRAP)}")
                identifiers.add(it.bytes)
            }
            /*
            Log.i(
                TAG,
                "Marking ${identifiers.size} temporary contact number(s) as potentially infectious=$wasPotentiallyInfectious ..."
            )*/
            val chunkSize = 998 // SQLITE_MAX_VARIABLE_NUMBER - 1
            identifiers.chunked(chunkSize).forEach {
                temporaryContactNumberDAO.update(it, wasPotentiallyInfectious)
                Log.i(
                    TAG,
                    "Marked ${it.size} temporary contact number(s) as potentially infectious=$wasPotentiallyInfectious"
                )
            }
        }

        val newInfections = temporaryContactNumberDAO.countNewPotentialInfections()
        if (newInfections > 0){
            val currPhone = context?.getSharedPreferences("org.covidwatch.android.PREFERENCE_FILE_KEY",
                Context.MODE_PRIVATE
            )?.getString("contact_number_full","No number saved")

            val cal = Calendar.getInstance()
            val currentTime = cal.time
            val simpleDateFormat = SimpleDateFormat(GlobalConstants.API_DATE_PATTERN)
            val formatted = simpleDateFormat.format(currentTime)

            currPhone?.let { ContactTracerStreams().postContactInformation(
                ContactRegistration(currPhone,formatted,true)
            )?.subscribe({
                //Do something on complete
                Log.i(TAG, "Posted phone number to server $currPhone...")
            })
            {
                Log.e(TAG, it.message!!)
                //Handle error
            }!! }
            temporaryContactNumberDAO.markPotentialInfectiousNotifiedToMedical()
            NotificationUtils.sendNotificationDanger()
        }
    }

    companion object {
        private const val TAG = "ReportsDownloadWorker"
        const val WORKER_NAME = "org.covidwatch.android.refresh"
    }
}