package com.pramaan.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FT012: the NL-query / reuse services are class-level read-only transactions that call ensureIndexed() -> clear().
 * The store's writes must run in their own transaction, otherwise Postgres rejects the DELETE inside the read-only one.
 * H2 does not enforce read-only, so this asserts the observable consequence: the write commits independently of the
 * surrounding transaction (it would be rolled back with it if it had joined).
 */
class PgVectorEmbeddingStoreWriteTest {

    @Test
    void clearRunsInItsOwnTransactionEvenInsideAReadOnlyOuterTransaction() {
        DataSource ds = new DriverManagerDataSource("jdbc:h2:mem:pgvwrite;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE evidence_embedding (evidence_id UUID PRIMARY KEY, sha256 VARCHAR(64))");
        jdbc.update("INSERT INTO evidence_embedding VALUES (RANDOM_UUID(), 'a')");
        PgVectorEmbeddingStore store = new PgVectorEmbeddingStore(jdbc, 8);

        TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(ds));
        outer.setReadOnly(true);
        outer.executeWithoutResult(status -> {
            store.clear();
            status.setRollbackOnly(); // had clear() joined this transaction, the delete would be undone here
        });

        assertThat(store.count()).isZero();
    }
}
