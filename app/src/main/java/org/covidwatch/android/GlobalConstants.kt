package org.covidwatch.android

import org.json.JSONObject

object GlobalConstants {
    const val CHANNEL_ID = "CovidWatchContactTracingNotificationChannel"
    const val INTERACTION_MIN_DISTANCE_IN_FEET = 8
    // private const val INTERACTION_DURATION_IN_MINUTES = 0.01 //.250 // equates to 15 seconds after math in logInteraction function
    const val INTERACTION_NOTIFY_WAIT_DURATION_IN_SECONDS = 30 // int
    const val INTERACTION_STALE_DURATION_IN_SECONDS = 30 // integer value
    const val DISTANCE_HISTORY_COUNT = 7 // # of historical distances
    const val INTERACTION_DELETE_DURATION_IN_DAYS = 21 // # of days to keep the interaction history

    const val SNOOZE_TIME_IN_SECONDS = 600

    const val API_DATE_PATTERN = "yyyy-MM-dd HH:mm"
    const val API_CT_KEY = "your api key"
    const val GETTING_CLOSE_DISTANCE_IN_FEET = 1.5
    const val TOO_CLOSE_DISTANCE_IN_FEET = .75
    //Time limit to wait before devices checks back for profile specific information for contacted device
    const val PHONE_PROFILE_CHECK_HOURS = 24L
    const val MEDICAL_NUMBER = "1 (555) 555-1234"
    var deviceDistanceProfile : JSONObject? = null
    const val WAIT_TO_DOWNLOAD_SIGNED_REPORT_IN_MINUTES :Long = 1
}