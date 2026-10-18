package db.migration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Seeds version 1 of the four Agent prompts from {@code prompts/v1/*.txt}. The checksum covers the template text,
 * so editing a published template makes Flyway validation fail instead of drifting silently; prompt fixes must be
 * shipped as a new version migration.
 */
public class V6__seed_agent_prompts extends BaseJavaMigration {
    private static final String[][] PROMPTS = {
            {"SCAMMER", "scammer"}, {"VICTIM", "victim"}, {"NARRATOR", "narrator"}, {"REPORT", "report"}};

    @Override
    public void migrate(Context context) throws Exception {
        try (var insert = context.getConnection().prepareStatement(
                "INSERT INTO agent_prompt (prompt_type, version_no, template_content, is_active) VALUES (?, 1, ?, 1)")) {
            for (String[] prompt : PROMPTS) {
                insert.setString(1, prompt[0]);
                insert.setString(2, read(prompt[1]));
                insert.executeUpdate();
            }
        }
    }

    @Override
    public Integer getChecksum() {
        var crc = new CRC32();
        for (String[] prompt : PROMPTS) crc.update(read(prompt[1]).getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    static String read(String name) {
        try (InputStream in = V6__seed_agent_prompts.class.getResourceAsStream("/prompts/v1/" + name + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt template " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
