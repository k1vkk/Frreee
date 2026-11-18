package com.example.data.remote

import com.example.BuildConfig
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
    @GET("api/health")
    suspend fun checkHealth(): Response<HealthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun getMe(@Header("Authorization") bearer: String): Response<MeResponse>

    @DELETE("api/auth/sessions/{sessionId}")
    suspend fun revokeSession(
        @Header("Authorization") bearer: String,
        @Path("sessionId") sessionId: String
    ): Response<Unit>

    @POST("api/auth/logout")
    suspend fun logout(@Header("Authorization") bearer: String): Response<Unit>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<BasicMessageResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<BasicMessageResponse>

    @GET("api/servers")
    suspend fun getServers(): Response<ServersResponse>

    @GET("api/servers/health")
    suspend fun getServersHealth(): Response<ServersHealthResponse>

    @GET("api/servers/best")
    suspend fun getBestServer(): Response<BestServerResponse>

    @POST("api/vpn/connect")
    suspend fun vpnConnect(
        @Header("Authorization") bearer: String,
        @Body body: ProvisionRequest
    ): Response<ProvisionResponse>

    @POST("api/vpn/register-peer")
    suspend fun registerPeer(
        @Header("Authorization") bearer: String,
        @Body body: ProvisionRequest
    ): Response<ProvisionResponse>

    @POST("api/vpn/disconnect")
    suspend fun vpnDisconnect(
        @Header("Authorization") bearer: String,
        @Body body: VpnDisconnectRequest
    ): Response<VpnDisconnectResponse>

    @GET("api/vpn/status")
    suspend fun getVpnStatus(
        @Header("Authorization") bearer: String,
        @Query("serverId") serverId: String? = null,
        @Query("clientPublicKey") clientPublicKey: String? = null
    ): Response<VpnStatusResponse>

    @GET("api/vpn/config")
    suspend fun getVpnConfig(
        @Header("Authorization") bearer: String,
        @Query("serverId") serverId: String,
        @Query("clientPublicKey") clientPublicKey: String
    ): Response<ProvisionResponse>

    @GET("api/speedtest/config")
    suspend fun getSpeedTestConfig(): Response<SpeedTestConfigResponse>

    @POST("api/speedtest/results")
    suspend fun uploadSpeedTestResult(
        @Header("Authorization") bearer: String,
        @Body body: SpeedTestUploadRequest
    ): Response<BasicMessageResponse>

    @GET("api/user/profile")
    suspend fun getUserProfile(@Header("Authorization") bearer: String): Response<UserProfileResponse>

    @GET("api/user/subscription")
    suspend fun getUserSubscription(@Header("Authorization") bearer: String): Response<SubscriptionMeResponse>

    @POST("api/subscriptions/verify")
    suspend fun verifyPurchase(
        @Header("Authorization") bearer: String,
        @Body body: VerifyPurchaseRequest
    ): Response<VerifyPurchaseResponse>

    @GET("api/support/tickets")
    suspend fun getSupportTickets(@Header("Authorization") bearer: String): Response<SupportTicketsResponse>

    @POST("api/support/tickets")
    suspend fun createSupportTicket(
        @Header("Authorization") bearer: String,
        @Body body: SupportTicketRequest
    ): Response<SupportTicketCreatedResponse>

    @POST("api/telemetry/error")
    suspend fun reportError(
        @Header("Authorization") bearer: String,
        @Body body: TelemetryErrorRequest
    ): Response<BasicMessageResponse>

    @GET("api/network/ip")
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
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .build()

    fun createService(baseUrl: String = NexoraConfig.DEFAULT_API_BASE_URL): NexoraApiService? {
        val clean = NexoraConfig.normalizeProductionHttpsUrl(baseUrl) ?: return null
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

    suspend fun fetchPublicIp(backendBaseUrl: String = NexoraConfig.DEFAULT_API_BASE_URL): Result<String> {
        val service = createService(backendBaseUrl)
        if (service != null) {
            try {
                val res = service.getObservedIp()
                val ip = res.body()?.ip
                if (res.isSuccessful && !ip.isNullOrBlank()) {
                    return Result.success(ip)
                }
            } catch (_: Exception) {
                // Fall back to external HTTPS IP check
            }
        }
        return try {
            val req = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .header("Cache-Control", "no-store")
                .build()
            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IllegalStateException("HTTP ${response.code}"))
                }
                val body = response.body?.string().orEmpty()
                val parsed = moshi.adapter(IpResponse::class.java).fromJson(body)
                val ip = parsed?.ip
                if (!ip.isNullOrBlank()) {
                    Result.success(ip)
                } else {
                    Result.failure(IllegalStateException("Empty IP response"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
