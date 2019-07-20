create table customers (
    customer_id  int8          not null primary key,
    name         varchar(256)  not null
);

insert into customers (customer_id, name) values (1, 'Javier');