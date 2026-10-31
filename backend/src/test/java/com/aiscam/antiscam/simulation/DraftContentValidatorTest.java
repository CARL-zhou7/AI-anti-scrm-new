package com.aiscam.antiscam.simulation;

import static org.junit.jupiter.api.Assertions.*;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.service.DraftContentValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class DraftContentValidatorTest {
    private final DraftContentValidator validator = new DraftContentValidator();

    @Test
    void manualEditsMayChangeDirectionAndAreNotSubjectToSemanticReview() {
        for (String text : List.of("您要不要再追加300元模拟资金，继续观察模拟收益？",
                "我帮您锁定信号，现在真投绝对能赶上！", "长".repeat(121))) {
            assertDoesNotThrow(() -> validator.validate(null, null, text, List.of()));
        }
    }

    @Test
    void emptyAndOversizedTextStillFails() {
        for (String text : List.of("  ", "长".repeat(2001))) {
            assertEquals(400, assertThrows(ApiException.class,
                    () -> validator.validate(null, null, text, List.of())).status);
        }
    }
}
