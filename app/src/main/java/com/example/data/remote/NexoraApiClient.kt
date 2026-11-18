package com.example.data.remote

import com.example.NexoraConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface NexoraApiService {
    @GET("api/v1/health")
    suspend fun checkHealth(): Response<HealthResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @GET("api/v1/auth/me")
    suspend fun getMe(@Header("Authorization") bearer: String): Response<MeResponse>

    @DELETE("api/v1/auth/sessions/{sessionId}")
    suspend fun revokeSession(
        @Header("Authorization") bearer: String,
        @Path("sessionId") sessionId: String
    ): Response<Unit>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<BasicMessageResponse>

    @POST("api/v1/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<BasicMessageResponse>

    @GET("api/v1/servers")
    suspend fun getServers(): Response<ServersResponse>

    @GET("api/v1/servers/best")
    suspend fun getBestServer(): Response<BestServerResponse>

    @POST("api/v1/vpn/connect")
    suspend fun vpnConnect(
        @Header("Authorization") bearer: String,
        @Body body: ProvisionRequest
    ): Response<ProvisionResponse>

    @POST("api/v1/vpn/disconnect")
    suspend fun vpnDisconnect(
        @Header("Authorization") bearer: String,
        @Body body: VpnDisconnectRequest
    ): Response<VpnDisconnectResponse>

    @GET("api/v1/vpn/status")
    suspend fun getVpnStatus(
        @Header("Authorization") bearer: String,
        @Query("serverId") serverId: String? = null,
        @Query("clientPublicKey") clientPublicKey: String? = null
    ): Response<VpnStatusResponse>

    @GET("api/v1/vpn/config")
    suspend fun getVpnConfig(
        @Header("Authorization") bearer: String,
        @Query("serverId") serverId: String,
        @Query("clientPublicKey") clientPublicKey: String
    ): Response<ProvisionResponse>

    @GET("api/v1/subscriptions/me")
    suspend fun getSubscription(@Header("Authorization") bearer: String): Response<SubscriptionMeResponse>

    @POST("api/v1/subscriptions/verify")
    suspend fun verifyPurchase(
        @Header("Authorization") bearer: String,
        @Body body: VerifyPurchaseRequest
    ): Response<VerifyPurchaseResponse>

    @POST("api/v1/telemetry/error")
    suspend fun reportError(
        @Header("Authorization") bearer: String,
        @Body body: TelemetryErrorRequest
    ): Response<BasicMessageResponse>

    @GET("api/v1/network/ip")
    suspend fun getObservedIp(): Response<IpResponse>
}

class NexoraApiClient {
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    fun createService(baseUrl: String = NexoraConfig.NEXORA_API_BASE_URL): NexoraApiService? {
        val clean = baseUrl.trim().trimEnd('/')
        val isLocalAllowed = NexoraConfig.IS_LOCAL_DEMO_MODE && NexoraConfig.isLocalHostUrl(clean)
        if (!clean.startsWith("https://") && !isLocalAllowed) return null
        if (!isLocalAllowed && (clean.contains("localhost") || clean.contains("127.0.0.1"))) return null
        return try {
            Retrofit.Builder()
                .baseUrl("$clean/")
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(NexoraApiService::class.java)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun fetchPublicIp(backendBaseUrl: String = NexoraConfig.NEXORA_API_BASE_URL): Result<String> {
        val service = createService(backendBaseUrl)
        if (service != null) {
            try {
                val res = service.getObservedIp()
                val ip = res.body()?.ip
                if (res.isSuccessful && !ip.isNullOrBlank()) {
                    return Result.success(ip)
                }
            } catch (_: Exception) {
                // Fall back to external public IP oracle
            }
        }
        return try {
            val req = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("Cache-Control", "no-store")
                .build()
            okHttpClient.newCall(req).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return if (NexoraConfig.IS_LOCAL_DEMO_MODE) {
                        Result.success("192.168.1.42")
                    } else {
                        Result.failure(IllegalStateException("HTTP ${response.code}"))
                    }
                }
                val parsed = moshi.adapter(IpResponse::class.java).fromJson(body)
                val ip = parsed?.ip
                if (!ip.isNullOrBlank()) {
                    Result.success(ip)
                } else if (NexoraConfig.IS_LOCAL_DEMO_MODE) {
                    Result.success("192.168.1.42")
                } else {
                    Result.failure(IllegalStateException("Empty IP response"))
                }
            }
        } catch (e: Exception) {
            if (NexoraConfig.IS_LOCAL_DEMO_MODE) {
                Result.success("192.168.1.42")
            } else {
                Result.failure(e)
            }
        }
    }
}
