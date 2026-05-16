package com.moreoffer;

import com.moreoffer.config.NiukeMcpProperties;
import com.moreoffer.config.OpenAiProperties;
import com.moreoffer.config.WebRooterMcpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        NiukeMcpProperties.class,
        OpenAiProperties.class,
        WebRooterMcpProperties.class
})
public class MoreOfferApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoreOfferApplication.class, args);
    }
}
