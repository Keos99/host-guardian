package com.example.guardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HostGuardianApplication {

    public static void main(String[] args) {
        SpringApplication.run(HostGuardianApplication.class, args);
    }
}
