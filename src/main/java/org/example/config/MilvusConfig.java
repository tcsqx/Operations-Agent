package org.example.config;

import io.milvus.client.MilvusServiceClient;
import org.example.client.MilvusClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * Milvus 配置类
 * 负责创建和管理 MilvusServiceClient Bean
 */
@Configuration
public class MilvusConfig {

    private static final Logger logger = LoggerFactory.getLogger(MilvusConfig.class);

    @Autowired
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;

    /**
     * 创建 MilvusServiceClient Bean
     * 
     * @return MilvusServiceClient 实例
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        try {
            milvusClient = milvusClientFactory.createClient();
            if (milvusClient != null) {
                logger.info("✅ Milvus 客户端初始化完成");
            }
            return milvusClient;
        } catch (Exception e) {
            logger.info("ℹ️ Milvus 未部署，已启用内置内存混合 RAG 引擎 ({})", e.getMessage());
            return null;
        }
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            logger.info("正在关闭 Milvus 客户端连接...");
            milvusClient.close();
            logger.info("Milvus 客户端连接已关闭");
        }
    }
}
