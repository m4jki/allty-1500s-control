package com.example.alltycontrol.domain

enum class ModeType(val protocolValue: Byte) {
    CONSTANT(0x01),
    FLASH(0x03),
    SOS(0x02),
}
