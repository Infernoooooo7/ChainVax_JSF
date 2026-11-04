package edu.nmamit.chainvax.simulator;

import java.math.BigDecimal;

import edu.nmamit.chainvax.common.Access;
import edu.nmamit.chainvax.simulator.SimulatorService.Mode;
import edu.nmamit.chainvax.simulator.SimulatorService.SimulatorState;
import edu.nmamit.chainvax.telemetry.IngestionService.IngestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulator")
@Tag(name = "Logger simulator")
public class SimulatorController {

    private final SimulatorService simulator;

    public SimulatorController(SimulatorService simulator) {
        this.simulator = simulator;
    }

    public record AutoRequest(boolean enabled) {
    }

    public record ModeRequest(@NotNull Mode mode) {
    }

    public record CustomReadingRequest(@NotNull @DecimalMin("-40.0") @DecimalMax("50.0") BigDecimal temperatureC) {
    }

    @GetMapping
    @PreAuthorize(Access.STAFF)
    public SimulatorState state() {
        return simulator.state();
    }

    @PostMapping("/auto")
    @PreAuthorize(Access.OPERATORS)
    @Operation(summary = "Turn periodic simulated readings on/off")
    public SimulatorState auto(@RequestBody AutoRequest request) {
        return simulator.setAuto(request.enabled());
    }

    @PostMapping("/devices/{deviceCode}/mode")
    @PreAuthorize(Access.OPERATORS)
    @Operation(summary = "Set a simulated logger's scenario (NORMAL/HEAT/COLD/SILENT); posts one reading immediately")
    public IngestResult mode(@PathVariable String deviceCode, @Valid @RequestBody ModeRequest request) {
        return simulator.setMode(deviceCode, request.mode());
    }

    @PostMapping("/devices/{deviceCode}/readings")
    @PreAuthorize(Access.OPERATORS)
    @Operation(summary = "Post one custom simulated reading through the logger ingestion API")
    public IngestResult reading(@PathVariable String deviceCode, @Valid @RequestBody CustomReadingRequest request) {
        return simulator.sendReading(deviceCode, request.temperatureC());
    }
}
