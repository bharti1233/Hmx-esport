package com.hmx.arena.core.designsystem

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(name = "hmx_theme")

/**
 * Persists the appearance preference. The default is SYSTEM so a first launch
 * follows the device until the player explicitly chooses.
 */
@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val mode = stringPreferencesKey("appearance_mode")
    }

    val mode: Flow<HmxThemeMode> = context.themeDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> HmxThemeMode.from(prefs[Keys.mode]) }

    suspend fun setMode(mode: HmxThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[Keys.mode] = mode.name
        }
    }
}

/**
 * Activity-scoped preference holder. Updates the in-memory state immediately
 * (so the UI switches in the same frame) and writes to DataStore in the
 * background. A null [mode] means the persisted value has not loaded yet; the
 * splash screen holds until it does, preventing a light-to-dark startup flash.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferences: ThemePreferences,
) : ViewModel() {

    private val _mode = MutableStateFlow<HmxThemeMode?>(null)
    val mode: StateFlow<HmxThemeMode?> = _mode.asStateFlow()

    val loaded: Boolean get() = _mode.value != null

    init {
        viewModelScope.launch {
            preferences.mode.collect { stored ->
                // Do not overwrite a just-selected value with a stale emission.
                if (_mode.value == null) _mode.value = stored
            }
        }
    }

    fun setMode(mode: HmxThemeMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        viewModelScope.launch { preferences.setMode(mode) }
    }
}
