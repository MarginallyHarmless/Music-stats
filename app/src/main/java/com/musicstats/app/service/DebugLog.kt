package com.musicstats.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DebugEventType {
    STATE, METADATA, TRACKING, SAVE, REJECT
}

data class DebugEvent(
    val timestamp: Long = System.currentTimeMillis(),
    val type: DebugEventType,
    val detail: String
) {
    val formattedTime: String
        get() = TIME_FORMAT.format(Date(timestamp))

    companion object {
        private val TIME_FORMAT = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }
}

object DebugLog {
    private const val MAX_EVENTS = 500

    private val events = mutableListOf<DebugEvent>()
    private val _flow = MutableStateFlow<List<DebugEvent>>(emptyList())
    val flow: StateFlow<List<DebugEvent>> = _flow.asStateFlow()

    @Synchronized
    fun log(type: DebugEventType, detail: String) {
        val event = DebugEvent(type = type, detail = detail)
        events.add(event)
        if (events.size > MAX_EVENTS) {
            events.removeAt(0)
        }
        _flow.value = events.toList()
    }

    @Synchronized
    fun clear() {
        events.clear()
        _flow.value = emptyList()
    }
}
