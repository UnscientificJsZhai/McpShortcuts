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
                // 这里可以添加更多的 Ktor 配置，比如超时、日志等
            }
        }
    }
}
