package db.migration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Pins the V1.2 narrator for new sessions without changing old prompt references. */
public class V11__seed_v12_narrator_prompt extends BaseJavaMigration {
    private static final String[] ROLES = {"narrator"};

    @Override public void migrate(Context context) throws Exception {
        try (var disable = context.getConnection().prepareStatement("UPDATE agent_prompt SET is_active=0 WHERE prompt_type=? AND is_active=1");
             var insert = context.getConnection().prepareStatement("INSERT INTO agent_prompt(prompt_type,version_no,template_content,is_active) VALUES (?,3,?,1)")) {
            for (String role : ROLES) {
                String type = role.toUpperCase(java.util.Locale.ROOT);
                disable.setString(1, type); disable.executeUpdate();
                insert.setString(1, type); insert.setString(2, read(role)); insert.executeUpdate();
            }
        }
    }

    @Override public Integer getChecksum() {
        var crc = new CRC32();
        for (String role : ROLES) crc.update(read(role).getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private static String read(String name) {
        try (InputStream in = V11__seed_v12_narrator_prompt.class.getResourceAsStream("/prompts/v3/" + name + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
