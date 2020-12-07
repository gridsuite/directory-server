CREATE TABLE IF NOT EXISTS directory.element (
    id timeuuid,
    name text static,
    type text static,
    parent_id timeuuid static,
    child_id timeuuid,
    child_name text,
    child_type text,
    child_isPrivate boolean,
    PRIMARY KEY ( id, child_id )
);
