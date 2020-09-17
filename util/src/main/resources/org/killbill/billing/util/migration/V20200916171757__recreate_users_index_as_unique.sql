drop index users_username on users;
create unique index users_username ON users(username);