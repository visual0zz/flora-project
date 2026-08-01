package com.flora.ai.spi;

/**
 * AI 提供者端点配置：服务地址与认证信息。
 */
public record Endpoint(String baseUrl, String apiKey) {

    public static Endpoint of(String baseUrl, String apiKey) {
        return new Endpoint(baseUrl, apiKey);
    }

    /** 便捷：无认证的端点。 */
    public static Endpoint of(String baseUrl) {
        return new Endpoint(baseUrl, null);
    }
}
