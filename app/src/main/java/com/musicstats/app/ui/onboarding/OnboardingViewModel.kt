package com.musicstats.app.ui.onboarding

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    fun nextStep() {
        _currentStep.value++
    }

    fun isNotificationListenerEnabled(): Boolean {
        val context = getApplication<Application>()
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val componentName = ComponentName(
            context,
            "com.musicstats.app.service.MusicNotificationListener"
        )
        return flat?.contains(componentName.flattenToString()) == true
    }

    fun markOnboardingComplete() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("music_stats", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
    }

    companion object {
        fun isOnboardingComplete(context: Context): Boolean {
            return context.getSharedPreferences("music_stats", Context.MODE_PRIVATE)
                .getBoolean("onboarding_complete", false)
        }
    }
}
