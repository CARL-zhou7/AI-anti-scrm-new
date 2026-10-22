package db.migration;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Completes the already-applied V8 structure and pins new role prompts without changing old snapshots. */
public class V9__seed_v12_role_prompts extends BaseJavaMigration {
    private static final String DDL = """
            ALTER TABLE simulation_fund_flow
              ADD COLUMN related_payment_id BIGINT UNSIGNED NULL,
              ADD INDEX idx_fund_related_payment (related_payment_id),
              ADD CONSTRAINT fk_fund_related_payment FOREIGN KEY (related_payment_id) REFERENCES simulation_fund_flow(id),
              ADD CONSTRAINT ck_fund_related_payment CHECK (
                (flow_type = 'REFUND' AND related_payment_id IS NOT NULL)
                OR (flow_type <> 'REFUND' AND related_payment_id IS NULL))
            """;
    private static final String[] ROLES = {"scammer", "victim", "narrator"};

    @Override public void migrate(Context context) throws Exception {
        try (var statement = context.getConnection().createStatement()) { statement.execute(DDL); }
        try (var disable = context.getConnection().prepareStatement("UPDATE agent_prompt SET is_active=0 WHERE prompt_type=? AND is_active=1");
             var insert = context.getConnection().prepareStatement("INSERT INTO agent_prompt(prompt_type,version_no,template_content,is_active) VALUES (?,2,?,1)")) {
            for (String role : ROLES) {
                String type = role.toUpperCase(java.util.Locale.ROOT);
                disable.setString(1, type); disable.executeUpdate();
                insert.setString(1, type); insert.setString(2, read(role)); insert.executeUpdate();
            }
        }
    }

    @Override public Integer getChecksum() {
        var crc = new CRC32();
        crc.update(DDL.getBytes(StandardCharsets.UTF_8));
        for (String role : ROLES) crc.update(read(role).getBytes(StandardCharsets.UTF_8));
        return (int) crc.getValue();
    }

    private static String read(String name) {
        try (InputStream in = V9__seed_v12_role_prompts.class.getResourceAsStream("/prompts/v2/" + name + ".txt")) {
            if (in == null) throw new IllegalStateException("Missing prompt " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
}
