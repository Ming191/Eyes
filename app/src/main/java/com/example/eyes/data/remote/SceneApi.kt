package com.example.eyes.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface SceneApi {
    /**
     * Uploads an image to the "describe" endpoint and obtains a scene description.
     *
     * @param image The image file included as a multipart part.
     * @return A SceneResponse containing the server's description of the uploaded image.
     */
    @Multipart
    @POST("describe")
    suspend fun describe(
        @Part image: MultipartBody.Part
    ): SceneResponse
}
