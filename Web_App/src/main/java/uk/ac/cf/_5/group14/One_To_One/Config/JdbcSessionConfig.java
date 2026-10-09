package uk.ac.cf._5.group14.One_To_One.Config;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.jdbc.PostgreSqlJdbcIndexedSessionRepositoryCustomizer;

@Configuration(proxyBeanMethods = false)
public class JdbcSessionConfig {

    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> concurrentSessionAttributes(
            DataSource dataSource) throws SQLException {
        String databaseProduct;
        try (var connection = dataSource.getConnection()) {
            databaseProduct = connection.getMetaData().getDatabaseProductName();
        }
        if ("PostgreSQL".equals(databaseProduct)) {
            return new PostgreSqlJdbcIndexedSessionRepositoryCustomizer();
        }
        if ("H2".equals(databaseProduct)) {
            // Two requests can load the same session before either creates its CSRF attribute.
            // Match Spring Session's PostgreSQL upsert behaviour for the local database.
            return repository -> repository.setCreateSessionAttributeQuery("""
                    MERGE INTO %TABLE_NAME%_ATTRIBUTES
                    (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES)
                    KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME) VALUES (?, ?, ?)
                    """);
        }
        return repository -> { };
    }
}
