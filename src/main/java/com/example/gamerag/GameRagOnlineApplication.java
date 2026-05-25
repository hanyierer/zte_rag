package com.example.gamerag;

import com.example.gamerag.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackageClasses = RagProperties.class)
public class GameRagOnlineApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameRagOnlineApplication.class, args);
    }
}
