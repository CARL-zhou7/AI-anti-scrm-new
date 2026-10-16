package com.aiscam.antiscam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AntiScamApplication {
    public static void main(String[] args) {
        SpringApplication.run(AntiScamApplication.class, args);
    }
}
