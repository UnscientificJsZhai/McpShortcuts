package com.unscientificjszhai.mcpshortcuts.mcp

import io.ktor.client.HttpClient
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MCP 客户端工厂接口。
 * 用于创建 MCP 客户端和传输层。
 */
interface McpClientFactory {
    /**
     * 创建一个新的 MCP 客户端。
     *
     * @return 返回初始化好的 [Client] 实例。
     */
    fun createClient(): Client

    /**
     * 创建一个基于 Ktor 的可流式传输的 HTTP 传输层。
     *
     * @param httpClient 用于通信的 [HttpClient] 实例。
     * @param url MCP 服务端的 URL。
     * @return 返回创建好的 [StreamableHttpClientTransport] 实例。
     */
    fun createTransport(httpClient: HttpClient, url: String): StreamableHttpClientTransport
}

/**
 * 默认的 MCP 客户端工厂实现。
 * 使用 Hilt 进行依赖注入。
 */
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

    override fun createTransport(
        httpClient: HttpClient,
        url: String
    ): StreamableHttpClientTransport {
        return StreamableHttpClientTransport(
            client = httpClient,
            url = url
        )
    }
}
