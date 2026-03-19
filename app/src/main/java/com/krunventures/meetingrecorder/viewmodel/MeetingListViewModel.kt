package com.krunventures.meetingrecorder.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.krunventures.meetingrecorder.MeetingApp
import kotlinx.coroutines.launch

class MeetingListViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as MeetingApp).database.meetingDao()
    val meetings = dao.getAll()

    fun deleteMeeting(id: Int) {
        viewModelScope.launch { dao.delete(id) }
    }
}
