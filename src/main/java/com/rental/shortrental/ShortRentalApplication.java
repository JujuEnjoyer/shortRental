package com.rental.shortrental;

import com.rental.shortrental.integration.avito.AvitoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(AvitoProperties.class)
public class ShortRentalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShortRentalApplication.class, args);
    }
}
