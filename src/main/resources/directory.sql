
    create table element (
       id uuid not null,
        description varchar(255),
        isPrivate boolean not null,
        name varchar(80),
        owner varchar(80) not null,
        parentId uuid,
        type varchar(30) not null,
        primary key (id)
    );
