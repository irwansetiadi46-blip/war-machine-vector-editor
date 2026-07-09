package com.example

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// --- Groq Models ---
data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>,
    @SerializedName("response_format") val responseFormat: GroqResponseFormat? = null
)

data class GroqMessage(
    val role: String,
    val content: String
)

data class GroqResponseFormat(
    val type: String
)

data class GroqResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqMessage
)

// --- Gemini Models ---
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

data class GeminiGenerationConfig(
    val responseMimeType: String? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

// --- Retrofit Interface ---
interface ApiService {
    @POST
    suspend fun getGroqCompletions(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body body: GroqRequest
    ): GroqResponse

    @POST
    suspend fun getGeminiContent(
        @Url url: String,
        @Body body: GeminiRequest
    ): GeminiResponse
}

object NetworkClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Base URL is required by Retrofit, but we override it with @Url
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.placeholder.com/") // overridden dynamically
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
