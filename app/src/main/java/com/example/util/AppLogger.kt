package com.example.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    fun log(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] $message"
        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry) // Add to the top of list
        if (currentList.size > 150) {
            currentList.removeLast()
        }
        _logs.value = currentList
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
