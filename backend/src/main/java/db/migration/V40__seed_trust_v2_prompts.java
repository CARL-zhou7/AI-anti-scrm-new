package db.migration;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Adds immutable versions; sessions continue to reference their original template rows. */
public class V40__seed_trust_v2_prompts extends BaseJavaMigration {
    private static final String[] TYPES = {"SCAMMER", "VICTIM", "NARRATOR", "EVENT"};
    private static final String[] FILES = {"v15/scammer", "v15/victim", "v15/narrator", "v15/event"};

    @Override public void migrate(Context context) throws Exception {
        try (var disable = context.getConnection().prepareStatement("UPDATE agent_prompt SET is_active=0 WHERE prompt_type=? AND is_active=1");
             var version = context.getConnection().prepareStatement("SELECT COALESCE(MAX(version_no),0)+1 FROM agent_prompt WHERE prompt_type=?");
             var insert = context.getConnection().prepareStatement("INSERT INTO agent_prompt(prompt_type,version_no,template_content,is_active) VALUES (?,?,?,1)")) {
            for (int i = 0; i < TYPES.length; i++) {
                disable.setString(1, TYPES[i]); disable.executeUpdate();
                insert.setString(1, TYPES[i]); version.setString(1, TYPES[i]);
                try (var result = version.executeQuery()) { result.next(); insert.setInt(2, result.getInt(1)); } insert.setString(3, read(FILES[i])); insert.executeUpdate();
            }
        }
    }

    @Override public Integer getChecksum() {
        var crc = new CRC32();
        for (String file : FILES) crc.update(read(file).getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private static String read(String file) {
        try (var in = V40__seed_trust_v2_prompts.class.getResourceAsStream("/prompts/" + file + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
