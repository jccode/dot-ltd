
create table stock
(
  id          int auto_increment primary key,
  name        varchar(32) null comment '股票名称',
  code        varchar(16) not null comment '股票代码',
  create_time datetime default CURRENT_TIMESTAMP not null,
  update_time datetime default CURRENT_TIMESTAMP not null
) comment '股票' charset = utf8;

create table company
(
  id          int auto_increment  primary key,
  name        varchar(32) not null comment '公司名称',
  full_name   varchar(32) null  comment '公司全称',
  stock_code  varchar(10) null  comment '股票代码',
  eng_name    varchar(128) null  comment '英文名',
  website     varchar(128) null  comment '公司网址',
  create_time datetime default CURRENT_TIMESTAMP not null,
  update_time datetime default CURRENT_TIMESTAMP not null,
  constraint company_name_uindex
  unique (name)
) comment '公司' charset = utf8;
