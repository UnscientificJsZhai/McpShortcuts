package com.unscientificjszhai.mcpshortcuts.mcp

import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import javax.inject.Inject
import javax.inject.Singleton

interface McpClientFactory {
    fun createClient(): Client
    fun createTransport(httpClient: HttpClient, url: String): StreamableHttpClientTransport
}

@Singleton
class DefaultMcpClientFactory @Inject constructor() : McpClientFactory {
    override fun createClient(): Client {
        return Client(
            clientInfo = Implementation(
                name = "McpShortcuts",
                version = "1.0"
            )
        )
    }

    override fun createTransport(httpClient: HttpClient, url: String): StreamableHttpClientTransport {
        return StreamableHttpClientTransport(
            client = httpClient,
            url = url
        )
    }
}
