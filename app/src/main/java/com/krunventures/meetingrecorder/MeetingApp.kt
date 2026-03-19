package com.krunventures.meetingrecorder

import android.app.Application
import com.krunventures.meetingrecorder.data.AppDatabase

class MeetingApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
