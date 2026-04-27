package com.example.eyes.data.remote

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface SceneApi {
    @Multipart
    @POST("describe")
    suspend fun describe(
        @Part image: MultipartBody.Part
    ): SceneResponse
}
