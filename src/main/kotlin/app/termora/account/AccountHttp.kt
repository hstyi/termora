package app.termora.account

import app.termora.Application
import app.termora.Application.ohMyJson
import app.termora.RSA
import app.termora.ResponseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.time.DateUtils
import org.slf4j.LoggerFactory

object AccountHttp {
    private val log = LoggerFactory.getLogger(AccountHttp::class.java)
    val client = Application.httpClient.newBuilder()
        .addInterceptor(SignatureInterceptor())
        .addInterceptor(AccessTokenInterceptor())
        .build()


    fun execute(client: OkHttpClient = AccountHttp.client, request: Request): String {
        val response = client.newCall(request).execute()
        if (response.isSuccessful.not()) {
            throw ResponseException(response.code, response)
        }

        val text = response.use { response.body.use { it?.string() } }
        if (text.isNullOrBlank()) {
            throw ResponseException(response.code, "response body is empty", response)
        }

        if (log.isDebugEnabled) {
            log.debug("url: ${request.url} response: $text")
        }

        return text
    }

    private class SignatureInterceptor : Interceptor {
        private val accountProperties get() = AccountProperties.getInstance()

        // @formatter:off
        private val publicKey by lazy { RSA.generatePublic(byteArrayOf(48, -126, 1, 34, 48, 13, 6, 9, 42, -122, 72, -122, -9, 13, 1, 1, 1, 5, 0, 3, -126, 1, 15, 0, 48, -126, 1, 10, 2, -126, 1, 1, 0, -60, 66, 87, -104, -41, -24, 6, 89, -128, -26, 119, 110, 57, 84, -20, -90, -23, 58, 86, -103, 84, -75, 127, -85, -122, 17, -104, 57, -92, 29, 96, 33, -100, 52, -12, -38, -75, 67, 107, -73, -56, 42, -33, -63, -125, -86, -56, 117, -42, 37, 37, 122, -15, -82, 33, -30, -107, 58, 58, -5, -128, 42, 91, 105, 32, 99, 105, -89, -92, -41, -81, 30, -40, 56, -45, 75, 5, -20, -22, 72, 80, 95, -97, -55, 19, 31, -109, 25, 72, -85, -3, 106, -104, 28, -21, 91, 101, 56, 97, -13, 7, -35, 112, 46, -30, -75, -9, -33, 0, -56, -128, -122, -128, -8, 12, 41, 127, -120, -5, 33, 14, 15, 118, 121, -85, 67, -8, -59, 68, -7, -42, 91, -56, 81, 96, -86, 15, -43, 7, -123, 122, 61, 21, 78, -60, -81, 15, 89, -106, -26, 66, -100, -92, -46, 67, -75, 93, 74, 104, 121, 9, -9, -128, 20, -20, -68, 32, -63, -10, -69, 42, -98, -34, -123, -110, 115, 119, 106, -61, 84, 91, 2, -54, -23, -72, 102, 24, 109, 96, 96, -2, 116, 79, -122, 88, -52, 113, -35, 70, 65, 13, -125, 99, 94, 120, 70, -127, 60, 77, 95, 35, 74, 72, 119, 122, -90, -22, -71, 36, -128, 41, -3, -28, 75, 92, 12, -64, -72, -6, -14, 0, -24, -84, -125, 57, 82, 85, 50, -28, -9, 45, 3, 101, 81, 43, -49, 123, -5, 104, 97, -28, 2, -98, -73, 49, -85, 2, 3, 1, 0, 1)) }
        // @formatter:on

        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            val signatureBase64 = response.headers("X-Signature").firstOrNull()
            val dataBase64 = response.headers("X-Signature-Data").firstOrNull()
            var signed: Boolean? = null

            if (signatureBase64.isNullOrBlank() || dataBase64.isNullOrBlank()) {
                signed = false
            } else {
                val signature = Base64.decodeBase64(signatureBase64)
                val data = Base64.decodeBase64(dataBase64)
                val json = ohMyJson.decodeFromString<JsonObject>(String(data))

                // 娇艳许可证日期
                val subscriptionExpiry = json["SubscriptionExpiry"]?.jsonPrimitive?.content
                if (subscriptionExpiry == null) {
                    signed = false
                } else {
                    val date = runCatching { DateUtils.parseDate(subscriptionExpiry, "yyyy-MM-dd") }.getOrNull()
                    if (date == null) {
                        signed = false
                    } else if (Application.getReleaseDate().after(date) || date.time < System.currentTimeMillis()) {
                        signed = false
                    }
                }

                // 校验许可证签名
                if (signed == null) {
                    signed = RSA.verify(publicKey, data, signature)
                }
            }

            if (accountProperties.signed != signed) {
                accountProperties.signed = signed
            }

            return response
        }
    }

    private class AccessTokenInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val builder = chain.request().newBuilder()
            val accountManager = AccountManager.getInstance()
            val accessToken = accountManager.getAccessToken()
            if (chain.request().header("Authorization") == null) {
                if (accessToken.isNotBlank()) {
                    builder.header("Authorization", "Bearer $accessToken")
                }
            }

            val response = chain.proceed(builder.build())
            if (response.code == 401) {
                val refreshToken = accountManager.getRefreshToken()
                if (refreshToken.isBlank()) {
                    accountManager.logout()
                    return response
                }

                synchronized(client) {
                    val refreshToken = accountManager.getRefreshToken()
                    if (refreshToken.isBlank()) {
                        return response
                    }

                    try {
                        // 刷新 token
                        accountManager.refreshToken()
                        // 重新请求
                        return chain.proceed(builder.build())
                    } catch (e: Exception) {
                        if (log.isErrorEnabled) {
                            log.error(e.message, e)
                        }
                    }
                }
            }

            return response
        }
    }
}