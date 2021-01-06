CREATE ROLE postgres WITH LOGIN;
CREATE TABLE element (
    parentId uuid,
    id uuid,
    name varchar(80),
    type varchar(20),
    isPrivate boolean,
    owner varchar(30)
);
GRANT ALL PRIVILEGES ON element TO postgres;
