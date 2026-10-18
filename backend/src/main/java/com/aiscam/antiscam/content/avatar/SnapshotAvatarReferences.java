package com.aiscam.antiscam.content.avatar;

/**
 * Lets the avatar endpoint serve portraits referenced by the caller's own simulation snapshots, even after the
 * profile is disabled or re-pictured. Implemented by the simulation module so content never depends on it.
 */
public interface SnapshotAvatarReferences {
    boolean referencedByUser(long userId, String storedKey);
}
