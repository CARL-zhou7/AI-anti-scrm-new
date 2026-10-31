package com.aiscam.antiscam.simulation.domain;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimulationReportTest {
    @Test
    void pendingSupportsTheV35ReportVersionAndKeepsLegacyVersions() {
        for (int version : List.of(1, 2, 3, 4)) {
            var report = SimulationReport.pending(112, Instant.EPOCH, version);
            assertEquals(version, report.getContentSchemaVersion());
            assertEquals(ReportStatus.PENDING, report.getStatus());
            assertEquals(0, report.getAttemptNo());
            report.claim("token", Instant.EPOCH, Instant.EPOCH.plusSeconds(120));
            assertEquals(ReportStatus.RUNNING, report.getStatus());
            assertEquals(1, report.getAttemptNo());
        }
        assertEquals(1, SimulationReport.pending(112, Instant.EPOCH).getContentSchemaVersion());
        for (int version : List.of(-1, 0, 5)) {
            assertThrows(IllegalArgumentException.class,
                    () -> SimulationReport.pending(112, Instant.EPOCH, version));
        }
    }
}
