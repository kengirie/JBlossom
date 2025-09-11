package io.github.kengirie.JBlossom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${blossom.cors.allowed-origins}")
    private String allowedOrigins;
    
    @Value("${blossom.cors.allowed-methods}")
    private String allowedMethods;
    
    @Value("${blossom.cors.allowed-headers}")
    private String allowedHeaders;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods(allowedMethods.split(","))
                .allowedHeaders(allowedHeaders.split(","))
                .allowCredentials(false)
                .maxAge(3600);
    }
}