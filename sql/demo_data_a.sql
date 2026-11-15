-- =====================================================
-- 迭代 2（挑战需求 A）演示数据（可选执行）
-- 用法：mysql -u root -p shop < demo_data_a.sql
--
-- 用途：录屏与验收演示用的初始数据，覆盖以下演示点：
--   1. 两级类别联动（一级/二级）
--   2. 多商品同时在售、各自独立队列
--   3. 库存 2 的商品：成交两次后自动下架（A.1-3）
--   4. 未上传图库的商品显示占位图（A.1-5）
--
-- 脚本幂等：用 NOT EXISTS 判断，可重复执行不会重复插入。
-- =====================================================

USE shop;

-- ---------- 一级类别 ----------
INSERT INTO category (name, parent_id, sort)
SELECT '服装', NULL, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '服装' AND parent_id IS NULL);

INSERT INTO category (name, parent_id, sort)
SELECT '数码', NULL, 2 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '数码' AND parent_id IS NULL);

-- ---------- 二级类别 ----------
INSERT INTO category (name, parent_id, sort)
SELECT '女装', c.id, 1 FROM category c
WHERE c.name = '服装' AND c.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM category x WHERE x.name = '女装' AND x.parent_id = c.id);

INSERT INTO category (name, parent_id, sort)
SELECT '男装', c.id, 2 FROM category c
WHERE c.name = '服装' AND c.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM category x WHERE x.name = '男装' AND x.parent_id = c.id);

INSERT INTO category (name, parent_id, sort)
SELECT '手机', c.id, 1 FROM category c
WHERE c.name = '数码' AND c.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM category x WHERE x.name = '手机' AND x.parent_id = c.id);

INSERT INTO category (name, parent_id, sort)
SELECT '耳机', c.id, 2 FROM category c
WHERE c.name = '数码' AND c.parent_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM category x WHERE x.name = '耳机' AND x.parent_id = c.id);

-- ---------- 商品（库存 2 / 1 / 5，均为无图，用于验证占位图） ----------
INSERT INTO product (name, category_id, description, image_url, images_json, price, stock, status)
SELECT '纯棉短袖T恤', c.id,
       '<p>100% 纯棉，<strong>夏季新款</strong>，尺码 S/M/L。</p>',
       NULL, NULL, 79.90, 2, 0
FROM category c
WHERE c.name = '女装'
  AND NOT EXISTS (SELECT 1 FROM product WHERE name = '纯棉短袖T恤');

INSERT INTO product (name, category_id, description, image_url, images_json, price, stock, status)
SELECT '无线蓝牙耳机', c.id,
       '<p>降噪蓝牙耳机，续航 <strong>30 小时</strong>。</p>',
       NULL, NULL, 199.00, 1, 0
FROM category c
WHERE c.name = '耳机'
  AND NOT EXISTS (SELECT 1 FROM product WHERE name = '无线蓝牙耳机');

INSERT INTO product (name, category_id, description, image_url, images_json, price, stock, status)
SELECT '智能手机 8+128G', c.id,
       '<p>6.5 英寸屏幕，8G 内存 + 128G 存储。</p>',
       NULL, NULL, 1299.00, 5, 0
FROM category c
WHERE c.name = '手机'
  AND NOT EXISTS (SELECT 1 FROM product WHERE name = '智能手机 8+128G');
