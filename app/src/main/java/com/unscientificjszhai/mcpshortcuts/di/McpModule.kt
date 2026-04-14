package com.unscientificjszhai.mcpshortcuts.di

import com.unscientificjszhai.mcpshortcuts.mcp.DefaultMcpClientFactory
import com.unscientificjszhai.mcpshortcuts.mcp.McpClientFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.sse.SSE
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class McpModule {

    @Binds
    @Singleton
    abstract fun bindMcpClientFactory(impl: DefaultMcpClientFactory): McpClientFactory

    companion object {
        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient {
            return HttpClient(OkHttp) {
                engine {
                    config {
                        // 必须设置，否则连接会因为超时自动断开
                        readTimeout(0, TimeUnit.MILLISECONDS)
                        connectTimeout(10, TimeUnit.SECONDS)
                    }
                }
                install(SSE)
            }
        }
    }
}
