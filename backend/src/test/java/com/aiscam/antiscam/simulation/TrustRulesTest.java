package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.simulation.rules.TrustRules;
import com.aiscam.antiscam.simulation.rules.TrustRules.RawEvent;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.aiscam.antiscam.simulation.rules.TrustEventType.*;
import static org.junit.jupiter.api.Assertions.*;

class TrustRulesTest {
    @Test
    void clampsEachEventAndRoundsHalfAwayFromZero() {
        var events = TrustRules.score(98, new BigDecimal("2.00"), BigDecimal.ONE,
                List.of(new RawEvent(NEW_EFFECTIVE_EXPLANATION, "explanation:fee"),
                        new RawEvent(NEW_CONTRADICTION, "material:amount")));
        assertEquals(2, events.getFirst().delta());
        assertEquals(95, events.getLast().scoreAfter());
        assertEquals(3, TrustRules.nominal(1, new BigDecimal("2.5")));
        assertEquals(-3, TrustRules.nominal(-1, new BigDecimal("2.5")));
        assertEquals(50, TrustRules.score(50, BigDecimal.ONE, BigDecimal.ONE,
                List.of(new RawEvent(REPEAT_NO_PROGRESS, "repeat:identity"))).getFirst().scoreAfter());
        assertEquals(0, TrustRules.score(1, BigDecimal.ONE, BigDecimal.ONE,
                List.of(new RawEvent(DECISIVE_COUNTEREVIDENCE, "identity:denied"))).getFirst().scoreAfter());
    }
}
