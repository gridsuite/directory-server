
    create table element (
       id uuid not null,
        isPrivate boolean not null,
        name varchar(255),
        owner varchar(255) not null,
        parentId uuid,
        type varchar(255) not null,
        primary key (id)
    );
