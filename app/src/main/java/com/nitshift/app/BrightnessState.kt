package com.nitshift.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object BrightnessState {
    private val _currentLux = MutableStateFlow(0f)
    val currentLux = _currentLux.asStateFlow()

    private val _calculatedBaseBrightness = MutableStateFlow(0f)
    val calculatedBaseBrightness = _calculatedBaseBrightness.asStateFlow()

    private val _appliedBrightness = MutableStateFlow(0)
    val appliedBrightness = _appliedBrightness.asStateFlow()

    private val _userOffset = MutableStateFlow(0)
    val userOffset = _userOffset.asStateFlow()

    private val _applyInBackground = MutableStateFlow(true)
    val applyInBackground = _applyInBackground.asStateFlow()

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled = _isServiceEnabled.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    // Persistent rolling log of light sensor and applied values
    private val _readingHistory = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val readingHistory = _readingHistory.asStateFlow()

    fun setCurrentLux(lux: Float) {
        _currentLux.value = lux
        addHistoryEntry(lux, _appliedBrightness.value)
    }

    fun setCalculatedBaseBrightness(base: Float) {
        _calculatedBaseBrightness.value = base
    }

    fun setAppliedBrightness(applied: Int) {
        _appliedBrightness.value = applied
    }

    fun setUserOffset(offset: Int) {
        _userOffset.value = offset
    }

    fun setApplyInBackground(enabled: Boolean) {
        _applyInBackground.value = enabled
    }

    fun setServiceEnabled(enabled: Boolean) {
        _isServiceEnabled.value = enabled
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _currentLux.value = 0f
            _calculatedBaseBrightness.value = 0f
            _appliedBrightness.value = 0
        }
    }

    private fun addHistoryEntry(lux: Float, applied: Int) {
        val entry = HistoryEntry(
            timestamp = System.currentTimeMillis(),
            lux = lux,
            appliedBrightness = applied
        )
        // Guard rolling window size
        val currentList = _readingHistory.value.toMutableList()
        currentList.add(0, entry)
        if (currentList.size > 20) {
            currentList.removeAt(currentList.lastIndex)
        }
        _readingHistory.value = currentList
    }

    fun clearHistory() {
        _readingHistory.value = emptyList()
    }
}

data class HistoryEntry(
    val timestamp: Long,
    val lux: Float,
    val appliedBrightness: Int
)
