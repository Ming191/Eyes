package com.example.eyes.objectdetection

data class YoloOutputInfo(
    val index: Int,
    val shape: List<Long>,
    val dtype: String,
    val elementCount: Long,
)
