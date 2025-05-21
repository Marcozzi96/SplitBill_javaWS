package it.javaWS.javaws.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;
import java.net.URI;

//@Configuration
public class DataSourceConfig {

    //@Value("${database.url}")
    private String databaseUrl;

    //@Bean
    public DataSource dataSource() {
        try {
            URI dbUri = new URI(databaseUrl);

            String username = dbUri.getUserInfo().split(":")[0];
            String password = dbUri.getUserInfo().split(":")[1];
            String url = "jdbc:postgresql://" + dbUri.getHost() + ':' + dbUri.getPort() + dbUri.getPath();

            return DataSourceBuilder.create()
                    .url(url)
                    .username(username)
                    .password(password)
                    .driverClassName("org.postgresql.Driver")
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Errore nel parsing di DATABASE_URL", e);
        }
    }
}
