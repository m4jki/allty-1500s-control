package com.example.alltycontrol.domain

data class LightMode(
    val id: String,
    val type: ModeType,
    val brightness: Int,
    val slot: Int = 1,
)
