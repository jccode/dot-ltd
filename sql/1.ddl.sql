
CREATE TABLE stock
(
    id int PRIMARY KEY NOT NULL AUTO_INCREMENT,
    name varchar(32) COMMENT '股票名称',
    code varchar(16) NOT NULL COMMENT '股票代码',
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null
) engine = InnoDB COLLATE=utf8_general_ci;
CREATE UNIQUE INDEX stock_code_uindex ON stock (code);
ALTER TABLE stock COMMENT = '股票';

CREATE TABLE company
(
    id int PRIMARY KEY AUTO_INCREMENT,
    name varchar(32) NOT NULL COMMENT '公司名称',
    full_name varchar(32) COMMENT '公司全称',
    stock_code varchar(10) COMMENT '股票代码',
    eng_name varchar(32) COMMENT '英文名',
    website varchar(32) COMMENT '公司网址',
    create_time datetime default CURRENT_TIMESTAMP not null,
    update_time datetime default CURRENT_TIMESTAMP not null
) engine = InnoDB COLLATE=utf8_general_ci;
CREATE UNIQUE INDEX company_name_uindex ON company (name);
ALTER TABLE company COMMENT = '公司';