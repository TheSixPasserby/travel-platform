-- 本脚本只操作调用方显式选择的数据库，不创建、不切换数据库。
-- 使用方式：mysql --database=<已确认的独立测试库> ... < database/seed.sql
-- 需在已由 schema.sql 建好表的空测试库中执行；可重复执行，结果为目的地 6 条、标签 8 条。

INSERT INTO destinations
  (name, destination_type, country, province, city, description, recommended_seasons, status, sort_order)
VALUES
  ('成都', 'CITY', '中国', '四川省', '成都市', '以美食、休闲生活和历史文化闻名的城市。', '春季、秋季', 'ACTIVE', 10),
  ('重庆', 'CITY', '中国', '重庆市', '重庆市', '山城地貌、夜景和特色美食丰富。', '春季、秋季', 'ACTIVE', 20),
  ('杭州', 'CITY', '中国', '浙江省', '杭州市', '以西湖、江南风光和人文景观著称。', '春季、秋季', 'ACTIVE', 30),
  ('西安', 'CITY', '中国', '陕西省', '西安市', '拥有丰富的历史遗迹和文化景观。', '春季、秋季', 'ACTIVE', 40),
  ('大理', 'CITY', '中国', '云南省', '大理白族自治州', '以苍山洱海和古城风貌吸引游客。', '春季、秋季', 'ACTIVE', 50),
  ('青岛', 'CITY', '中国', '山东省', '青岛市', '海滨风光、城市建筑和海鲜美食。', '夏季、秋季', 'ACTIVE', 60)
AS incoming
ON DUPLICATE KEY UPDATE
  description = incoming.description,
  recommended_seasons = incoming.recommended_seasons;

INSERT INTO tags (name)
VALUES
  ('美食'), ('自然风光'), ('人文历史'), ('城市漫游'),
  ('亲子旅行'), ('徒步'), ('摄影'), ('周末短途')
AS incoming
ON DUPLICATE KEY UPDATE name = incoming.name;
