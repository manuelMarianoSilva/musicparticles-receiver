package com.particle.receiver.audio

import com.particle.receiver.data.TouchEvent

interface Instrument {
    fun handle(event: TouchEvent)
}