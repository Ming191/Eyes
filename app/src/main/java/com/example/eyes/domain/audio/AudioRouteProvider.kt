package com.example.eyes.domain.audio

interface AudioRouteProvider {
    fun isHeadsetConnected(): Boolean
}
