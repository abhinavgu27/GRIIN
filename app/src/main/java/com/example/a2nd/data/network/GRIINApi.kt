package com.example.a2nd.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Aligned with Spring Boot expectations
data class HazardReport(
    val latitude: Double,
    val longitude: Double,
    val severity: Double
)

interface GRIINApiService {
    @POST("api/v1/hazards")
    suspend fun reportHazard(@Body report: HazardReport)

    @GET("api/v1/hazards")
    suspend fun getAllHazards(): List<HazardReport>
}

object RetrofitClient {
    // Production endpoint on Railway
    private const val BASE_URL = "https://griin-server-production.up.railway.app/"

    val apiService: GRIINApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GRIINApiService::class.java)
    }
}
