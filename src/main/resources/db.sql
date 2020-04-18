-- sxwpan 设置
create database sxwpan_test charset = utf8;
CREATE USER 'sxwpan'@'%' IDENTIFIED BY 'sxw_789SXW';
GRANT ALL PRIVILEGES ON sxwpan.* TO 'sxwpan'@'%' WITH GRANT OPTION ;
FLUSH PRIVILEGES;