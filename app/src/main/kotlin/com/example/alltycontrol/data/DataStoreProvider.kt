package com.example.alltycontrol.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

val Context.alltyDataStore by preferencesDataStore(name = "allty_settings")
