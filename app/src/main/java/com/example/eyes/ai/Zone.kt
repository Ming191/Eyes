package com.example.eyes.ai

import com.example.eyes.i18n.AppLanguage

enum class Zone(val labelVi: String) {
    LEFT("bên trái"),
    CENTER("chính giữa"),
    RIGHT("bên phải");

    fun label(language: AppLanguage): String = when (language) {
        AppLanguage.VI -> labelVi
        AppLanguage.EN -> when (this) {
            LEFT -> "bên trái"
            CENTER -> "chính giữa"
            RIGHT -> "bên phải"
        }
    }
}
