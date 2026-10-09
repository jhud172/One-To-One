package uk.ac.cf._5.group14.One_To_One.Security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import uk.ac.cf._5.group14.One_To_One.Config.JdbcSessionConfig;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSessionPersistenceTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        if (database != null) database.shutdown();
    }

    private JdbcIndexedSessionRepository configuredRepository() throws Exception {
        var repository = new JdbcIndexedSessionRepository(jdbcTemplate, transactionTemplate);
        new JdbcSessionConfig().concurrentSessionAttributes(database).customize(repository);
        return repository;
    }

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(database));
        jdbcTemplate.execute("""
                CREATE TABLE SPRING_SESSION (
                    PRIMARY_ID CHAR(36) NOT NULL PRIMARY KEY,
                    SESSION_ID CHAR(36) NOT NULL,
                    CREATION_TIME BIGINT NOT NULL,
                    LAST_ACCESS_TIME BIGINT NOT NULL,
                    MAX_INACTIVE_INTERVAL INT NOT NULL,
                    EXPIRY_TIME BIGINT NOT NULL,
                    PRINCIPAL_NAME VARCHAR(100)
                )
                """);
        jdbcTemplate.execute("CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID)");
        jdbcTemplate.execute("CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME)");
        jdbcTemplate.execute("CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME)");
        jdbcTemplate.execute("""
                CREATE TABLE SPRING_SESSION_ATTRIBUTES (
                    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
                    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
                    ATTRIBUTE_BYTES BINARY LARGE OBJECT NOT NULL,
                    PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
                    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK
                        FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES SPRING_SESSION (PRIMARY_ID)
                            ON DELETE CASCADE
                )
                """);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aNewRepositoryInstanceCanRestoreAnAuthenticatedSession() throws Exception {
        JdbcIndexedSessionRepository firstInstance = configuredRepository();
        SessionRepository firstRepository = firstInstance;
        Session session = (Session) firstRepository.createSession();
        session.setAttribute("authenticated-user", "synthetic-client");
        firstRepository.save(session);

        JdbcIndexedSessionRepository restartedInstance = configuredRepository();
        SessionRepository restartedRepository = restartedInstance;
        Session restored = (Session) restartedRepository.findById(session.getId());

        assertThat(restored).isNotNull();
        assertThat(restored.<String>getAttribute("authenticated-user")).isEqualTo("synthetic-client");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void simultaneousFirstCsrfWritesKeepOneAttributeAndBothRequestsOtherChanges() throws Exception {
        SessionRepository repository = configuredRepository();
        Session initial = (Session) repository.createSession();
        initial.setAttribute("authenticated-user", "synthetic-client");
        repository.save(initial);
        Session first = (Session) repository.findById(initial.getId());
        Session second = (Session) repository.findById(initial.getId());
        String csrfAttribute = "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";
        first.setAttribute(csrfAttribute, new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "synthetic-first"));
        second.setAttribute(csrfAttribute, new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "synthetic-second"));
        first.setAttribute("first-request", "retained");
        second.setAttribute("second-request", "retained");
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstSave = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                repository.save(first);
                return null;
            });
            var secondSave = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                repository.save(second);
                return null;
            });
            firstSave.get(10, TimeUnit.SECONDS);
            secondSave.get(10, TimeUnit.SECONDS);
        }
        Session restored = (Session) repository.findById(initial.getId());
        assertThat(restored.<DefaultCsrfToken>getAttribute(csrfAttribute).getToken())
                .isIn("synthetic-first", "synthetic-second");
        assertThat(restored.<String>getAttribute("authenticated-user")).isEqualTo("synthetic-client");
        assertThat(restored.<String>getAttribute("first-request")).isEqualTo("retained");
        assertThat(restored.<String>getAttribute("second-request")).isEqualTo("retained");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE ATTRIBUTE_NAME = ?",
                Integer.class, csrfAttribute)).isEqualTo(1);

        restored.setAttribute(csrfAttribute, new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "synthetic-update"));
        repository.save(restored);
        restored = (Session) repository.findById(initial.getId());
        assertThat(restored.<DefaultCsrfToken>getAttribute(csrfAttribute).getToken()).isEqualTo("synthetic-update");
        repository.deleteById(initial.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES", Integer.class))
                .isZero();
    }
}
