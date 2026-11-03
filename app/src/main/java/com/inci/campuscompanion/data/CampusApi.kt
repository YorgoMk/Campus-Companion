package com.inci.campuscompanion.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface CampusApi {
    @GET("events/event?sortBy=id&order=asc")
    suspend fun getEvents(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): List<EventDto>

    companion object {
        fun create(): CampusApi = Retrofit.Builder()
            .baseUrl("https://6ad70838f71f154ac4879fac.mockapi.io/api/v1/")
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory(MediaType.get("application/json"))
            )
            .build()
            .create(CampusApi::class.java)
    }
}
