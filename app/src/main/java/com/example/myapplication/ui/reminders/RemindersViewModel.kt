package com.example.myapplication.ui.reminders

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RemindersViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is reminders Fragment"
    }
    val text: LiveData<String> = _text
}