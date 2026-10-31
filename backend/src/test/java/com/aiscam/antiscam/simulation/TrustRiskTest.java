package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.simulation.rules.TrustRiskRules;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrustRiskTest {
    @Test void trustAndBehaviourIndependentlyAffectRisk() {
        for (int behaviour : new int[]{0,25,50,75,100}) {
            int previous=-1;
            for (int trust=0;trust<=100;trust++) {
                int risk=TrustRiskRules.score(trust,behaviour,false,false);
                assertTrue(risk >= previous && risk >= 0 && risk <= 100);
                previous=risk;
            }
        }
        assertEquals(46,TrustRiskRules.score(60,25,false,false));
        assertEquals(52,TrustRiskRules.score(70,25,false,false));
        assertTrue(TrustRiskRules.score(70,75,false,false) > TrustRiskRules.score(70,25,false,false));
        assertEquals(82,TrustRiskRules.score(70,25,true,false));
        assertEquals(42,TrustRiskRules.score(70,100,true,true));
        assertTrue(TrustRiskRules.reason(true,true,"已拒绝追加").contains("停止配合"));
    }
}
