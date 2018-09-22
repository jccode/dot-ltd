
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

