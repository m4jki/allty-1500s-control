package com.example.alltycontrol

import android.app.Application
import com.example.alltycontrol.ble.AlltyBleManager
import com.example.alltycontrol.data.ModeRepository
import com.example.alltycontrol.data.SettingsRepository
import com.example.alltycontrol.data.alltyDataStore

class AlltyApplication : Application() {
    val bleManager by lazy { AlltyBleManager(this) }
    val modeRepository by lazy { ModeRepository(alltyDataStore) }
    val settingsRepository by lazy { SettingsRepository(alltyDataStore) }
}
