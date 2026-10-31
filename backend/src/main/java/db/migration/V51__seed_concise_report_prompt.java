package db.migration;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** New immutable report prompt; existing sessions retain their pinned template. */
public class V51__seed_concise_report_prompt extends BaseJavaMigration {
    private static final String TYPE = "REPORT";
    private static final String FILE = "v20/report";

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
        try (var in = V51__seed_concise_report_prompt.class.getResourceAsStream("/prompts/" + FILE + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt " + FILE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
