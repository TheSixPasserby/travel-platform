package com.example.travelplatform.ai.config;

import com.example.travelplatform.ai.service.AiGenerationGate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    @Bean
    AiGenerationGate aiGenerationGate() { return new AiGenerationGate(); }
}
