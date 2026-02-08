package sigma.persistence.dialect;

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * H2 trigger implementation for updating sequence_number on insert/update.
 * This is required because H2 doesn't support PL/pgSQL or PL/SQL syntax.
 */
public class H2SequenceTrigger implements Trigger {

    @Override
    public void init(Connection conn, String schemaName, String triggerName,
                     String tableName, boolean before, int type) throws SQLException {
        // Initialization - nothing needed
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        if (newRow != null) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT NEXT VALUE FOR dynamic_documents_seq_num");
                if (rs.next()) {
                    // sequence_number is at index 12 (0-based, after all other columns)
                    // The column order is: id, table_name, data, version, is_deleted,
                    // latest_request_id, created_by, last_modified_by, created_at,
                    // last_modified_at, sequence_number
                    newRow[10] = rs.getLong(1);
                }
            }
        }
    }

    @Override
    public void close() throws SQLException {
        // Cleanup - nothing needed
    }

    @Override
    public void remove() throws SQLException {
        // Removal - nothing needed
    }
}
