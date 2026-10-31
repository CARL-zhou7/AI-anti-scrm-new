package db.migration;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Adds immutable versions; sessions continue to reference their original template rows. */
public class V31__improve_dialogue_and_payment_progression extends BaseJavaMigration {
    private static final String[] TYPES = {"VICTIM", "SCAMMER"};
    private static final String[] FILES = {"v9/victim", "v10/scammer"};
    private static final int[] VERSIONS = {8, 9};

    @Override public void migrate(Context context) throws Exception {
        try (var disable = context.getConnection().prepareStatement("UPDATE agent_prompt SET is_active=0 WHERE prompt_type=? AND is_active=1");
             var insert = context.getConnection().prepareStatement("INSERT INTO agent_prompt(prompt_type,version_no,template_content,is_active) VALUES (?,?,?,1)")) {
            for (int i = 0; i < TYPES.length; i++) {
                disable.setString(1, TYPES[i]); disable.executeUpdate();
                insert.setString(1, TYPES[i]); insert.setInt(2, VERSIONS[i]); insert.setString(3, read(FILES[i])); insert.executeUpdate();
            }
        }
    }

    @Override public Integer getChecksum() {
        var crc = new CRC32();
        for (String file : FILES) crc.update(read(file).getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private static String read(String file) {
        try (var in = V31__improve_dialogue_and_payment_progression.class.getResourceAsStream("/prompts/" + file + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
