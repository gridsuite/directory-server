package org.gridsuite.directory.test;

import ru.yandex.qatools.embed.postgresql.EmbeddedPostgres;

import java.io.File;
import java.net.URL;

import static ru.yandex.qatools.embed.postgresql.distribution.Version.V10_6;

public class PostgreSQLServer extends org.junit.rules.ExternalResource {

    private static EmbeddedPostgres postgres;

    @Override
    protected void before() throws Throwable {
        synchronized (PostgreSQLServer.class) {
            if (postgres == null) {
                postgres = new EmbeddedPostgres(V10_6);
                final String url = postgres.start("localhost", 5432, "my_test_db");
                URL resource = getClass().getClassLoader().getResource("test_db.sql");
                if (resource == null) {
                    throw new IllegalArgumentException("file not found!");
                } else {
                    postgres.getProcess().get().importFromFile(new File(resource.toURI()));
                }
            }
        }
    }

    @Override
    protected void after() {
        if (postgres != null) {
            postgres.stop();
        }

    }
}
