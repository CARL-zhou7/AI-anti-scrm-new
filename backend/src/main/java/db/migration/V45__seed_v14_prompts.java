package db.migration;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * V1.4 activates the 15-round prompt generation for SCAMMER, EVENT and NARRATOR only. VICTIM and REPORT keep their
 * active rows, and every existing session continues to read the pinned prompt id it started with.
 */
public class V45__seed_v14_prompts extends BaseJavaMigration {
    private static final String[] TYPES = {"SCAMMER", "EVENT", "NARRATOR"};
    private static final String[] FILES = {"v18/scammer", "v18/event", "v18/narrator"};

    @Override public void migrate(Context context) throws Exception {
        try (var disable = context.getConnection().prepareStatement("UPDATE agent_prompt SET is_active=0 WHERE prompt_type=? AND is_active=1");
             var version = context.getConnection().prepareStatement("SELECT COALESCE(MAX(version_no),0)+1 FROM agent_prompt WHERE prompt_type=?");
             var insert = context.getConnection().prepareStatement("INSERT INTO agent_prompt(prompt_type,version_no,template_content,is_active) VALUES (?,?,?,1)")) {
            for (int i = 0; i < TYPES.length; i++) {
                disable.setString(1, TYPES[i]); disable.executeUpdate();
                insert.setString(1, TYPES[i]); version.setString(1, TYPES[i]);
                try (var result = version.executeQuery()) { result.next(); insert.setInt(2, result.getInt(1)); }
                insert.setString(3, read(FILES[i])); insert.executeUpdate();
            }
        }
    }

    @Override public Integer getChecksum() {
        var crc = new CRC32();
        for (String file : FILES) crc.update(read(file).getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private static String read(String file) {
        try (var in = V45__seed_v14_prompts.class.getResourceAsStream("/prompts/" + file + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
