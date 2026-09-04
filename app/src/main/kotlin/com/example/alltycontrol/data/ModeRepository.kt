package com.example.alltycontrol.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.alltycontrol.ble.AlltyProtocol
import com.example.alltycontrol.domain.LightMode
import com.example.alltycontrol.domain.ModeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModeRepository(private val dataStore: DataStore<Preferences>) {
    private val modesKey = stringSetPreferencesKey("custom_modes")

    val modes: Flow<List<LightMode>> = dataStore.data.map { preferences ->
        preferences[modesKey]
            .orEmpty()
            .mapNotNull(::decode)
            .sortedBy { it.slot }
    }

    suspend fun add(mode: LightMode) {
        dataStore.edit { preferences ->
            preferences[modesKey] = preferences[modesKey].orEmpty() + encode(mode)
        }
    }

    suspend fun remove(id: String) {
        dataStore.edit { preferences ->
            preferences[modesKey] = preferences[modesKey]
                .orEmpty()
                .mapNotNull(::decode)
                .filterNot { it.id == id }
                .sortedBy { it.slot }
                .mapIndexed { index, mode -> encode(mode.copy(slot = index + 1)) }
                .toSet()
        }
    }

    /** Migrates legacy records and repairs duplicate/missing slots without losing local modes. */
    suspend fun normalizeSlots(): List<LightMode> {
        var normalized = emptyList<LightMode>()
        dataStore.edit { preferences ->
            val decoded = preferences[modesKey]
                .orEmpty()
                .mapNotNull(::decode)
                .sortedWith(compareBy<LightMode> { it.slot }.thenBy { it.type.ordinal }.thenBy { it.brightness })
            val used = mutableSetOf<Int>()
            normalized = decoded.map { mode ->
                val slot = mode.slot.takeIf {
                    it in 1..AlltyProtocol.MAX_CUSTOM_MODES && used.add(it)
                } ?: (1..AlltyProtocol.MAX_CUSTOM_MODES).first { it !in used }.also(used::add)
                mode.copy(slot = slot)
            }.sortedBy { it.slot }
            preferences[modesKey] = normalized.map(::encode).toSet()
        }
        return normalized
    }

    suspend fun clear() {
        dataStore.edit { it.remove(modesKey) }
    }

    private fun encode(mode: LightMode): String =
        "${mode.id}|${mode.type.name}|${mode.brightness}|${mode.slot}"

    private fun decode(value: String): LightMode? {
        val parts = value.split('|')
        if (parts.size !in 3..4) return null
        val type = runCatching { ModeType.valueOf(parts[1]) }.getOrNull() ?: return null
        val brightness = parts[2].toIntOrNull()?.takeIf { it in 1..100 } ?: return null
        val slot = if (parts.size == 4) parts[3].toIntOrNull() ?: 1 else 1
        return LightMode(parts[0], type, brightness, slot)
    }
}
