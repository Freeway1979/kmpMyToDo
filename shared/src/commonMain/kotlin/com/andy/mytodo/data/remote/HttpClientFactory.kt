package com.andy.mytodo.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * One configured client for the whole app. Only the [engine] is platform
 * specific (OkHttp on Android, Darwin/NSURLSession on iOS, MockEngine in tests).
 */
fun createHttpClient(engine: HttpClientEngine, baseUrl: String, enableLogging: Boolean = false): HttpClient =
    HttpClient(engine) {
        // Non-2xx responses throw ResponseException instead of being parsed as data.
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true // tolerate new server fields
                explicitNulls = false
            })
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 15_000
        }
        if (enableLogging) {
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.INFO
            }
        }
        defaultRequest { url(baseUrl.trimEnd('/') + "/") }
    }
