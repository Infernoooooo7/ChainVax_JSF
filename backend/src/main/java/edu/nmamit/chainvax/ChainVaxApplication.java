package edu.nmamit.chainvax;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableScheduling
public class ChainVaxApplication {

    public static void main(String[] args) {
        // All timestamps are UTC. Also avoids Windows' legacy zone id "Asia/Calcutta", which the
        // PostgreSQL 17 Docker image rejects when the JDBC driver sends the session TimeZone.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ChainVaxApplication.class, args);
    }
}
