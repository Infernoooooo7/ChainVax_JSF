package edu.nmamit.chainvax.telemetry;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoggerDeviceRepository extends JpaRepository<LoggerDevice, Long> {

    Optional<LoggerDevice> findByDeviceCode(String deviceCode);

    List<LoggerDevice> findAllByOrderByIdAsc();
}
