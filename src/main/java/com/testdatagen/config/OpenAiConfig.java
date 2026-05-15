package com.testdatagen.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
public class OpenAiConfig {

    @Bean
    @ConfigurationProperties(prefix = "app.openai")
    public OpenAiProperties openAiProperties() {
        return new OpenAiProperties();
    }

    @Bean
    public RestTemplate openAiRestTemplate(OpenAiProperties props) {
        int timeoutMs = props.getTimeoutSeconds() * 1000;

        try {
            // 每次请求都使用全新的 SSL 上下文，避免 SSL session 复用问题
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            TrustManager[] trustManagers = tmf.getTrustManagers();
            X509TrustManager trustManager = (X509TrustManager) trustManagers[0];

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new java.security.SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient client = new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, trustManager)
                    // 完全禁用连接池 - 每次请求创建新连接
                    .connectionPool(new ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                    .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .retryOnConnectionFailure(true)
                    .followRedirects(false)
                    .build();

            OkHttp3ClientHttpRequestFactory factory = new OkHttp3ClientHttpRequestFactory(client);
            return new RestTemplate(factory);

        } catch (Exception e) {
            throw new RuntimeException("初始化 OkHttp SSL 配置失败", e);
        }
    }

    public static class OpenAiProperties {
        private String apiKey = "tp-cisncg8bwcp4be3o8gt7jp3qkbcellf96yu914r0bngqpkhm";
        private String baseUrl = "https://token-plan-cn.xiaomimimo.com/";
        private String model = "mimo-v2.5-pro";
        private int maxTokens = 10000;
        private double temperature = 0.8;
        private int timeoutSeconds = 60;
        private boolean enableThinking = false;
        private Map<String, ModelConfig> models = new HashMap<>();

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public Map<String, ModelConfig> getModels() { return models; }
        public void setModels(Map<String, ModelConfig> models) { this.models = models; }

        public boolean isEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(boolean enableThinking) {
            this.enableThinking = enableThinking;
        }

        /**
         * 根据模型ID获取对应配置，如果没有则返回默认配置
         */
        public ModelConfig getModelConfig(String modelId) {
            if (modelId != null && models.containsKey(modelId)) {
                return models.get(modelId);
            }
            // 返回默认配置
            ModelConfig defaultConfig = new ModelConfig();
            defaultConfig.setBaseUrl(this.baseUrl);
            defaultConfig.setApiKey(this.apiKey);
            defaultConfig.setName(this.model);
            return defaultConfig;
        }
    }

    public static class ModelConfig {
        private String baseUrl;
        private String apiKey;
        private String name;
        private Integer maxTokens;
        private Double temperature;
        private Integer timeoutSeconds;
        private Boolean enableThinking;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getMaxTokens() { return maxTokens; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
        public Integer getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public Boolean getEnableThinking() { return enableThinking; }
        public void setEnableThinking(Boolean enableThinking) { this.enableThinking = enableThinking; }
    }
}
