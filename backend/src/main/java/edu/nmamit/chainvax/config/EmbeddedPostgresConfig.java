package edu.nmamit.chainvax.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Zero-install demo database: starts real PostgreSQL binaries (zonky embedded-postgres,
 * Apache-2.0) inside the application process. Disable with the "postgres" profile to use an
 * external PostgreSQL server instead.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "chainvax.embedded-db.enabled", havingValue = "true")
public class EmbeddedPostgresConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedPostgresConfig.class);

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres(ChainVaxProperties properties) throws IOException {
        ChainVaxProperties.EmbeddedDb cfg = properties.embeddedDb();
        int port = cfg.port();
        if (port > 0 && !isFree(port)) {
            // e.g. a previous run was force-killed and its PostgreSQL is still running
            log.warn("Port {} is busy (left over from an earlier run?) - starting the demo database on a free port instead.", port);
            port = 0;
        }
        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder()
                .setPort(port) // 0 = pick a free port
                .setCleanDataDirectory(cfg.cleanOnStart());
        if (cfg.dataDir() != null && !cfg.dataDir().isBlank()) {
            builder.setDataDirectory(Path.of(cfg.dataDir()));
        }
        EmbeddedPostgres postgres = builder.start();
        log.info("Embedded PostgreSQL (demo database) listening on localhost:{}", postgres.getPort());
        return postgres;
    }

    private static boolean isFree(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            socket.setReuseAddress(false);
            return true;
        }
        catch (IOException ex) {
            return false;
        }
    }

    @Bean
    @Primary
    DataSource dataSource(EmbeddedPostgres postgres) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("chainvax-embedded-pg");
        dataSource.setJdbcUrl("jdbc:postgresql://localhost:" + postgres.getPort() + "/postgres");
        dataSource.setUsername("postgres");
        dataSource.setMaximumPoolSize(12);
        return dataSource;
    }
}
