package com.example.eyes.infrastructure.objectdetection

object YoloModelMetadata {
    val cocoLabelKeys = List(80) { index -> "coco_$index" }
}
