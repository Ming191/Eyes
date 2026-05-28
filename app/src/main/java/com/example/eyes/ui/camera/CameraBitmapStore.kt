package com.example.eyes.ui.camera

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference

internal class CameraBitmapStore {
    private val latestFrame = AtomicReference<Bitmap?>(null)

    fun replaceLatestFrame(bitmap: Bitmap) {
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val previous = latestFrame.getAndSet(copy)
        recycle(previous)
    }

    fun getLatestFrame(): Bitmap? = latestFrame.get()

    fun clear() {
        recycle(latestFrame.getAndSet(null))
    }

    fun recycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }
}
