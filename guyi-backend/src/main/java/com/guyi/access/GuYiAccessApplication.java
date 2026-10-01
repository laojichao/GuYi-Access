package com.guyi.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class GuYiAccessApplication {

    public static void main(String[] args) {
        // Pin the JVM clock to the same zone the JDBC session (serverTimezone) and Jackson use.
        // Otherwise LocalDateTime.now() - card expiry, login timestamps - and the database's
        // CURRENT_TIMESTAMP - device cleanup, dashboard - can differ by hours in a container running
        // in UTC, so a card could look active in one query and expired in another.
        // Override with -Dapp.timezone=UTC or the APP_TIMEZONE environment variable.
        String zone = System.getProperty("app.timezone",
                System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Shanghai"));
        TimeZone.setDefault(TimeZone.getTimeZone(zone));

        SpringApplication.run(GuYiAccessApplication.class, args);
    }
}
