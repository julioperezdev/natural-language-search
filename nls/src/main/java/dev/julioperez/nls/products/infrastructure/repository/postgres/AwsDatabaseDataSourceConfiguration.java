package dev.julioperez.nls.products.infrastructure.repository.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class AwsDatabaseDataSourceConfiguration {
    @Bean
    @Primary
    @ConditionalOnExpression("'${nls.database.secret-id:}'.trim().length() > 0")
    HikariDataSource dataSource(
            AwsDatabaseCredentialsProvider credentialsProvider,
            NlsDatabaseProperties properties) {
        AwsDatabaseCredentialsProvider.DatabaseConnectionSecret secret =
                credentialsProvider.load(properties.secretId());
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(secret.jdbcUrl());
        configuration.setUsername(secret.username());
        configuration.setPassword(secret.password());
        configuration.setSchema(properties.schema());
        configuration.setPoolName("nls-database");
        return new HikariDataSource(configuration);
    }
}
