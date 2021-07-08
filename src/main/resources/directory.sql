
    create table element (
       id uuid not null,
        isPrivate boolean not null,
        name varchar(80),
        owner varchar(80) not null,
        parentId uuid,
        type varchar(20) not null,
        primary key (id)
    );
