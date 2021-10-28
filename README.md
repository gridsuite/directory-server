# directory-server

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets.
After you generated a changeset do not forget to add it to git and in src/resource/db/changelog/db.changelog-master.yml


The old way to automatically generate the sql schema file (directly using hibernate) can still be used for debugging. Use the following command:
```
mvn package -DskipTests && rm -f src/main/resources/directory.sql && java  -jar target/gridsuite-directory-server-1.0.0-SNAPSHOT-exec.jar --spring.jpa.properties.javax.persistence.schema-generation.scripts.action=create 
```
