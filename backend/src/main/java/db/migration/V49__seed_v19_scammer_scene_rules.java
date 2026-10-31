package db.migration;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Phase 3 adds one variable to the scammer prompt only: the scene rules frozen into the session snapshot at start.
 * A new version row keeps every pinned session on the exact prompt it started with, exactly like V45 did for V1.4.
 * EVENT, NARRATOR, VICTIM and REPORT keep their active rows.
 */
public class V49__seed_v19_scammer_scene_rules extends BaseJavaMigration {
    private static final String TYPE = "SCAMMER";
    private static final String FILE = "v19/scammer";

    @Override public void migrate(Context context) throws Exception {
        try (var disable = context.getConnection()
                    .prepareStatement("UPDATE agent_prompt SET is_active=0 WHERE prompt_type=? AND is_active=1");
             var version = context.getConnection()
                    .prepareStatement("SELECT COALESCE(MAX(version_no),0)+1 FROM agent_prompt WHERE prompt_type=?");
             var insert = context.getConnection()
                    .prepareStatement("INSERT INTO agent_prompt(prompt_type,version_no,template_content,is_active) VALUES (?,?,?,1)")) {
            disable.setString(1, TYPE);
            disable.executeUpdate();
            version.setString(1, TYPE);
            insert.setString(1, TYPE);
            try (var result = version.executeQuery()) {
                result.next();
                insert.setInt(2, result.getInt(1));
            }
            insert.setString(3, read());
            insert.executeUpdate();
        }
    }

    @Override public Integer getChecksum() {
        var crc = new CRC32();
        crc.update(read().getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private static String read() {
        try (var in = V49__seed_v19_scammer_scene_rules.class.getResourceAsStream("/prompts/" + FILE + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt " + FILE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
