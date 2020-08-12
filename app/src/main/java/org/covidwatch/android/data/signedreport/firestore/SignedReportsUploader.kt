package org.covidwatch.android.data.signedreport.firestore

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.covidwatch.android.BuildConfig
import org.covidwatch.android.GlobalConstants
import org.covidwatch.android.data.signedreport.SignedReport
import org.covidwatch.android.data.signedreport.SignedReportDAO
import org.covidwatch.android.model.ContactRegistration
import org.covidwatch.android.service.ContactTracerStreams
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SignedReportsUploader(var context: Context,
    private val okHttpClient: OkHttpClient,
    private val signedReportDAO: SignedReportDAO
) {
    private val TAG = this::class.qualifiedName
    fun startUploading() {
        GlobalScope.launch(Dispatchers.IO) {
            signedReportDAO.all().collect {
                uploadSignedReportsNeeded(it)
            }
        }
    }

    private fun uploadSignedReportsNeeded(signedReports: List<SignedReport>) {
        val signedReportsToUpload = signedReports.filter {
            it.uploadState == SignedReport.UploadState.NOTUPLOADED
        }
        uploadContactEvents(signedReportsToUpload)
    }

    private fun uploadContactEvents(signedReports: List<SignedReport>) {
        if (signedReports.isEmpty()) return
        signedReports.forEach { signedReport ->

            val signatureBytes = base64String(signedReport.signatureBytes)

            Log.i(TAG, "Uploading signed report ($signatureBytes)...")

            signedReport.uploadState = SignedReport.UploadState.UPLOADING
            signedReportDAO.update(signedReport)

            val isUploaded = try {
                val json = signedReport.toJson()
                uploadReport(json)
            } catch (e: Exception) {
                Log.e(TAG, "Uploading signed report ($signatureBytes) failed", e)
                false
            }
            if (isUploaded) {
                uploaded(signedReport)
                Log.i(TAG, "Uploaded signed report ($signatureBytes)")
            } else {
                notUploaded(signedReport)
                Log.e(TAG, "Uploading signed report ($signatureBytes) failed")
            }
        }
    }

    private fun uploaded(signedReport: SignedReport) {
        signedReport.uploadState = SignedReport.UploadState.UPLOADED
        signedReportDAO.update(signedReport)
    }

    private fun notUploaded(signedReport: SignedReport) {
        signedReport.uploadState = SignedReport.UploadState.NOTUPLOADED
        signedReportDAO.update(signedReport)
    }

    private fun SignedReport.toJson(): String {
        val data = mapOf(
            FirestoreConstants.FIELD_TEMPORARY_CONTACT_KEY_BYTES to base64String(
                temporaryContactKeyBytes
            ),
            FirestoreConstants.FIELD_START_INDEX to startIndex,
            FirestoreConstants.FIELD_END_INDEX to endIndex,
            FirestoreConstants.FIELD_MEMO_DATA to base64String(memoData),
            FirestoreConstants.FIELD_MEMO_TYPE to memoType,
            FirestoreConstants.FIELD_REPORT_VERIFICATION_PUBLIC_KEY_BYTES to base64String(
                reportVerificationPublicKeyBytes
            ),
            FirestoreConstants.FIELD_SIGNATURE_BYTES to base64String(signatureBytes)
        )

        return JSONObject(data).toString()
    }

    private fun base64String(input: ByteArray): String {
        return Base64.encodeToString(input, Base64.NO_WRAP)
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class, IllegalStateException::class)
    private fun uploadReport(json: String): Boolean {
        //post phone number to backend
        val currPhone = context.getSharedPreferences("org.covidwatch.android.PREFERENCE_FILE_KEY",
            Context.MODE_PRIVATE
        )?.getString("contact_number_full","No number saved")
        val cal = Calendar.getInstance()
        val currentTime = cal.time
        val simpleDateFormat = SimpleDateFormat(GlobalConstants.API_DATE_PATTERN)
        val formatted = simpleDateFormat.format(currentTime)

        currPhone?.let { ContactTracerStreams().postContactInformation(ContactRegistration(currPhone,formatted,true))?.subscribe({
            //Do something on complete
            Log.i(TAG, "Posted phone number to server $currPhone...")
             })
        {
            Log.e(TAG, it.message!!)
            //Handle error
        }!! }

        //submit firebase report
        val apiUrl = BuildConfig.FIREBASE_CLOUD_FUNCTIONS_ENDPOINT
        val url = "$apiUrl/submitReport"
        val body = json.toRequestBody(contentType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    private fun contentType(): MediaType {
        return "application/json; charset=utf-8".toMediaType()
    }
}
