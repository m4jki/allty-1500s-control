package com.example.alltycontrol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private val lastDeviceAddressKey = stringPreferencesKey("last_device_address")
    private val lightSensorKey = booleanPreferencesKey("light_sensor_enabled")
    private val motionSensorKey = booleanPreferencesKey("motion_sensor_enabled")

    val lastDeviceAddress: Flow<String?> = dataStore.data.map { it[lastDeviceAddressKey] }
    val lightSensorEnabled: Flow<Boolean> = dataStore.data.map { it[lightSensorKey] ?: false }
    val motionSensorEnabled: Flow<Boolean> = dataStore.data.map { it[motionSensorKey] ?: false }

    suspend fun setLastDeviceAddress(address: String) {
        dataStore.edit { it[lastDeviceAddressKey] = address }
    }

    suspend fun setLightSensorEnabled(enabled: Boolean) {
        dataStore.edit { it[lightSensorKey] = enabled }
    }

    suspend fun setMotionSensorEnabled(enabled: Boolean) {
        dataStore.edit { it[motionSensorKey] = enabled }
    }
}
