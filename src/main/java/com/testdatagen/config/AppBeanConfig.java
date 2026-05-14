package com.testdatagen.config;

import com.testdatagen.util.EncryptionUtil;
import com.testdatagen.util.JdbcConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppBeanConfig {

    @Value("${app.encryption.secret-key:default-dev-key-change-me-in-production}")
    private String encryptionKey;

    @Bean
    public EncryptionUtil encryptionUtil() {
        return new EncryptionUtil(encryptionKey);
    }

    @Bean
    public JdbcConnectionFactory jdbcConnectionFactory(EncryptionUtil encryptionUtil) {
        return new JdbcConnectionFactory(encryptionUtil);
    }
}
