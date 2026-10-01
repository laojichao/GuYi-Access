package com.guyi.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GuYiAccessApplication {

    public static void main(String[] args) {
        SpringApplication.run(GuYiAccessApplication.class, args);
    }
}
