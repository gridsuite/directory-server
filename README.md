# directory-server

   To automatically generate the sql schema file you can use the following command:
   
    mvn package -DskipTests && rm -f src/main/resources/directory.sql && java  -jar target/gridsuite-directory-server-1.0.0-SNAPSHOT-exec.jar --spring.jpa.properties.javax.persistence.schema-generation.scripts.action=create 
