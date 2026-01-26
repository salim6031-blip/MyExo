package com.example.myexo1.utils

//import android.os.Build
//import androidx.annotation.RequiresApi
//import java.time.ZoneId
import java.util.TimeZone

class TimeZoneHelper {

    companion object {
//        fun getCurrentTimeZoneId(): String {
//            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                getModernTimeZoneId()
//            } else {
//                getLegacyTimeZoneId()
//            }
//        }

//        @RequiresApi(Build.VERSION_CODES.O)
//        private fun getModernTimeZoneId(): String {
//            return ZoneId.systemDefault().id
//        }

//        private fun getLegacyTimeZoneId(): String {
//            return TimeZone.getDefault().id
//        }

        fun getCurrentTimeZoneDisplayName(): String {
            return TimeZone.getDefault().displayName
        }

        fun getCurrentUTCOffsetFormatted(): Int {
            val tz = TimeZone.getDefault()
            val offsetMillis = tz.getOffset(System.currentTimeMillis())
            val hours = offsetMillis / (1000 * 60 * 60)
            //val minutes = Math.abs(offsetMillis / (1000 * 60) % 60)
            return hours
            //return String.format("UTC%+03d:%02d", hours, minutes)
        }
    }
}