package com.thecrime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TheCrimeApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TheCrimeApplication.class, args);
    }
}
