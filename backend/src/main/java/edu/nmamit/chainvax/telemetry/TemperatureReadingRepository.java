package edu.nmamit.chainvax.telemetry;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemperatureReadingRepository extends JpaRepository<TemperatureReading, Long> {

    List<TemperatureReading> findByDeviceIdAndRecordedAtGreaterThanEqualOrderByRecordedAtAsc(Long deviceId, Instant since);
}
