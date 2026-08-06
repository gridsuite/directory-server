# Directory Server

[![Actions Status](https://github.com/gridsuite/directory-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/directory-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Adirectory-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Adirectory-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

The **directory-server** is a microservice of the [GridSuite](https://github.com/gridsuite) platform dedicated to **managing the hierarchical directory tree** in which all user resources (studies, contingency lists, filters, …) are organised, along with the **access permissions** controlling who can browse or modify each directory.

It provides the following capabilities:

- **Manage root directories and sub-directories**: create, rename, delete, and move directories in a tree structure.
- **Manage directory elements**: insert, update, duplicate, move, and delete elements of any type (study, filter, contingency list, …) referenced by their UUID.
- **Enforce access permissions**: each directory carries READ / WRITE / MANAGE permission sets. Only users (or user groups) with the appropriate permission can browse or modify a directory and its contents.
- **Resolve element paths**: retrieve the full ancestor path of any element up to the root directory.
- **Search elements** full-text across the tree using an Elasticsearch index.
- **Update element status**: mark elements (and all their descendants) as `CREATED` or `DELETING`.
- **Notify** other microservices via RabbitMQ whenever the directory tree changes (element added, renamed, moved, deleted).

---

## Technical Stack

- Spring Boot (Web, Data JPA, Actuator, Cloud Stream)
- PostgreSQL + Liquibase
- Elasticsearch (`spring-data-elasticsearch`)
- RabbitMQ via Spring Cloud Stream
- API documentation: OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus

---

## Development Scripts

Build Docker image:

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. After you generated a changeset do not forget to add it to git and in `src/main/resources/db/changelog/db.changelog-master.yml`.


---

## Interactions with Other Microservices

The directory-server publishes messages on RabbitMQ and calls **user-admin-server** to resolve user groups for permission checks.

```text
┌──────────────────────┐
│   directory-server   │──► user-admin-server  (resolve user groups for permission checks)
└──────────────────────┘
          ▼
       RabbitMQ (publishDirectoryUpdate — emitted on every directory or element change)
```

