package app.termora.account

import app.termora.Application
import app.termora.ResponseException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.slf4j.LoggerFactory

object AccountHttp {
    private val log = LoggerFactory.getLogger(AccountHttp::class.java)
    val client = Application.httpClient.newBuilder()
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