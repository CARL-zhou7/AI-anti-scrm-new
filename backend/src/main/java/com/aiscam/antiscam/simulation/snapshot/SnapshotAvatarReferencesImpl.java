package com.aiscam.antiscam.simulation.snapshot;

import com.aiscam.antiscam.content.avatar.SnapshotAvatarReferences;
import com.aiscam.antiscam.simulation.domain.SimulationSessionRepository;
import org.springframework.stereotype.Component;

/** Portraits frozen in one of the caller's own simulation snapshots stay readable for that caller only. */
@Component
public class SnapshotAvatarReferencesImpl implements SnapshotAvatarReferences {
    private final SimulationSessionRepository sessions;
    public SnapshotAvatarReferencesImpl(SimulationSessionRepository sessions) { this.sessions = sessions; }

    @Override
    public boolean referencedByUser(long userId, String storedKey) {
        return sessions.countAvatarReferences(userId, storedKey) > 0;
    }
}
