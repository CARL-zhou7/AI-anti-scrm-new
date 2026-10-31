package com.aiscam.antiscam.simulation;

import com.aiscam.antiscam.media.TempAudioStore;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.service.InterventionStateAssembler;
import com.aiscam.antiscam.simulation.service.SimulationStateAssembler;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SimulationStateAssemblerWiringTest {
    @Test void springUsesTheConstructorWithInterventionSupport() {
        var interventions = mock(InterventionStateAssembler.class);
        new ApplicationContextRunner()
                .withUserConfiguration(SimulationStateAssembler.class)
                .withBean(SimulationRoundRepository.class, () -> mock(SimulationRoundRepository.class))
                .withBean(SimulationMessageRepository.class, () -> mock(SimulationMessageRepository.class))
                .withBean(SimulationReportRepository.class, () -> mock(SimulationReportRepository.class))
                .withBean(ConfigurationSnapshots.class, () -> mock(ConfigurationSnapshots.class))
                .withBean(TempAudioStore.class, () -> mock(TempAudioStore.class))
                .withBean(SimulationTrustEventRepository.class, () -> mock(SimulationTrustEventRepository.class))
                .withBean(InterventionStateAssembler.class, () -> interventions)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SimulationStateAssembler.class);
                    assertThat(ReflectionTestUtils.getField(context.getBean(SimulationStateAssembler.class), "interventions"))
                            .isSameAs(interventions);
                });
    }
}
