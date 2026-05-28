package com.example.eyes.domain.image

data class ImageFrame(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val rotationDegrees: Int = 0,
    val timestampMillis: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImageFrame) return false

        if (!data.contentEquals(other.data)) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (format != other.format) return false
        if (rotationDegrees != other.rotationDegrees) return false
        if (timestampMillis != other.timestampMillis) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + format.hashCode()
        result = 31 * result + rotationDegrees
        result = 31 * result + (timestampMillis?.hashCode() ?: 0)
        return result
    }
}

enum class ImageFormat {
    JPEG,
    NV21,
    RGBA_8888,
    YUV_420_888,
    UNKNOWN
}
