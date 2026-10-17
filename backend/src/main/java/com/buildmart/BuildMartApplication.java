package com.buildmart;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.buildmart.mapper")
public class BuildMartApplication {
    public static void main(String[] args) {
        SpringApplication.run(BuildMartApplication.class, args);
    }
}
