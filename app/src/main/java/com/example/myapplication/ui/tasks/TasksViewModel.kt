package com.example.myapplication.ui.tasks

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class TasksViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is tasks Fragment"
    }
    val text: LiveData<String> = _text
}