package com.inoagent.vpn.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** Ktor entry point for a future authenticated config-download endpoint on the service site. */
class ConfigDownloadClient(private val client: HttpClient = HttpClient()) {
    suspend fun download(url: String): String = client.get(url).body()
    fun close() = client.close()
}

