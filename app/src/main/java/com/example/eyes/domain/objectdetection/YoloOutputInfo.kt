package com.example.eyes.domain.objectdetection

data class YoloOutputInfo(
    val index: Int,
    val shape: List<Long>,
    val dtype: String,
    val elementCount: Long,
)
