-- Consolidated and executable category quality patch script.
-- Generated at: 2026-03-18 19:31:58
-- Included: only SQL files validated to execute successfully in current DB.
-- Excluded due syntax/encoding corruption: patch_category_labels_zh.sql, patch_category_refine.sql, patch_category_generic_reduction_v4.sql, patch_category_manual_finalize_v6.sql, patch_precision_fix_false_monitors_v7.sql.


-- ===== BEGIN patch_category_granularity.sql =====
BEGIN;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS category_level1 TEXT,
    ADD COLUMN IF NOT EXISTS category_level2 TEXT,
    ADD COLUMN IF NOT EXISTS category_level3 TEXT,
    ADD COLUMN IF NOT EXISTS category_path TEXT;

CREATE INDEX IF NOT EXISTS idx_products_category_l1 ON products(category_level1);
CREATE INDEX IF NOT EXISTS idx_products_category_l2 ON products(category_level2);
CREATE INDEX IF NOT EXISTS idx_products_category_l3 ON products(category_level3);

-- Fast backfill from existing coarse category to keep DB values non-null.
UPDATE products
SET
    category_level1 = COALESCE(NULLIF(category_level1, ''), 'General'),
    category_level2 = COALESCE(NULLIF(category_level2, ''), COALESCE(NULLIF(category, ''), 'General Goods')),
    category_level3 = COALESCE(NULLIF(category_level3, ''), 'General Goods')
WHERE category_level1 IS NULL
   OR category_level2 IS NULL
   OR category_level3 IS NULL
   OR category_level1 = ''
   OR category_level2 = ''
   OR category_level3 = '';

UPDATE products
SET category_path = category_level1 || ' > ' || category_level2 || ' > ' || category_level3
WHERE category_path IS NULL
   OR category_path = '';

COMMIT;
-- ===== END patch_category_granularity.sql =====

-- ===== BEGIN patch_category_dense_coverage_v2.sql =====
BEGIN;

WITH product_text AS (
    SELECT
        id,
        lower(
            coalesce(name, '') || ' ' ||
            coalesce(brand, '') || ' ' ||
            coalesce(description, '')
        ) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '办公文具',
    category_level2 = '办公设备',
    category_level3 = '打印/装订/纸品',
    category_path = '办公文具 > 办公设备 > 打印/装订/纸品',
    category = '办公文具 > 办公设备 > 打印/装订/纸品'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%printer%' OR t.txt LIKE '%shredder%' OR t.txt LIKE '%scanner%'
      OR t.txt LIKE '%binder%' OR t.txt LIKE '%label%' OR t.txt LIKE '%paper%'
      OR t.txt LIKE '%stapler%' OR t.txt LIKE '%办公%' OR t.txt LIKE '%打印机%'
      OR t.txt LIKE '%碎纸机%' OR t.txt LIKE '%扫描仪%' OR t.txt LIKE '%活页夹%'
      OR t.txt LIKE '%标签纸%' OR t.txt LIKE '%订书机%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '办公文具',
    category_level2 = '文具耗材',
    category_level3 = '书写/本册',
    category_path = '办公文具 > 文具耗材 > 书写/本册',
    category = '办公文具 > 文具耗材 > 书写/本册'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%pen%' OR t.txt LIKE '%pencil%' OR t.txt LIKE '%marker%'
      OR t.txt LIKE '%notebook%' OR t.txt LIKE '%journal%' OR t.txt LIKE '%文具%'
      OR t.txt LIKE '%中性笔%' OR t.txt LIKE '%铅笔%' OR t.txt LIKE '%记号笔%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '家居生活',
    category_level2 = '家具',
    category_level3 = '客厅/卧室家具',
    category_path = '家居生活 > 家具 > 客厅/卧室家具',
    category = '家居生活 > 家具 > 客厅/卧室家具'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%sofa%' OR t.txt LIKE '%mattress%' OR t.txt LIKE '%bed frame%'
      OR t.txt LIKE '%wardrobe%' OR t.txt LIKE '%desk%' OR t.txt LIKE '%table%'
      OR t.txt LIKE '%chair%' OR t.txt LIKE '%futon%' OR t.txt LIKE '%沙发%'
      OR t.txt LIKE '%床垫%' OR t.txt LIKE '%床架%' OR t.txt LIKE '%衣柜%'
      OR t.txt LIKE '%书桌%' OR t.txt LIKE '%餐桌%' OR t.txt LIKE '%椅子%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '家居生活',
    category_level2 = '家装灯具',
    category_level3 = '灯具照明',
    category_path = '家居生活 > 家装灯具 > 灯具照明',
    category = '家居生活 > 家装灯具 > 灯具照明'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%lamp%' OR t.txt LIKE '%lighting%' OR t.txt LIKE '%led light%'
      OR t.txt LIKE '%ceiling fan%' OR t.txt LIKE '%台灯%' OR t.txt LIKE '%吊灯%'
      OR t.txt LIKE '%灯带%' OR t.txt LIKE '%照明%' OR t.txt LIKE '%风扇灯%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '家居生活',
    category_level2 = '家纺布艺',
    category_level3 = '窗帘/地毯',
    category_path = '家居生活 > 家纺布艺 > 窗帘/地毯',
    category = '家居生活 > 家纺布艺 > 窗帘/地毯'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%curtain%' OR t.txt LIKE '%carpet%' OR t.txt LIKE '%rug%'
      OR t.txt LIKE '%mat%' OR t.txt LIKE '%窗帘%' OR t.txt LIKE '%地毯%'
      OR t.txt LIKE '%地垫%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '母婴用品',
    category_level2 = '婴童用品',
    category_level3 = '喂养/出行/寝居',
    category_path = '母婴用品 > 婴童用品 > 喂养/出行/寝居',
    category = '母婴用品 > 婴童用品 > 喂养/出行/寝居'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%baby%' OR t.txt LIKE '%infant%' OR t.txt LIKE '%newborn%'
      OR t.txt LIKE '%crib%' OR t.txt LIKE '%stroller%' OR t.txt LIKE '%diaper%'
      OR t.txt LIKE '%婴儿%' OR t.txt LIKE '%宝宝%' OR t.txt LIKE '%新生儿%'
      OR t.txt LIKE '%婴儿床%' OR t.txt LIKE '%推车%' OR t.txt LIKE '%尿布%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '母婴玩具',
    category_level2 = '玩具乐器',
    category_level3 = '益智/派对玩具',
    category_path = '母婴玩具 > 玩具乐器 > 益智/派对玩具',
    category = '母婴玩具 > 玩具乐器 > 益智/派对玩具'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%toy%' OR t.txt LIKE '%lego%' OR t.txt LIKE '%puzzle%'
      OR t.txt LIKE '%doll%' OR t.txt LIKE '%party%' OR t.txt LIKE '%mask%'
      OR t.txt LIKE '%玩具%' OR t.txt LIKE '%拼图%' OR t.txt LIKE '%积木%'
      OR t.txt LIKE '%娃娃%' OR t.txt LIKE '%派对%' OR t.txt LIKE '%面具%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '食品保健',
    category_level2 = '营养保健',
    category_level3 = '维矿/功能补充',
    category_path = '食品保健 > 营养保健 > 维矿/功能补充',
    category = '食品保健 > 营养保健 > 维矿/功能补充'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%vitamin%' OR t.txt LIKE '%supplement%' OR t.txt LIKE '%probiotic%'
      OR t.txt LIKE '%fish oil%' OR t.txt LIKE '%capsule%' OR t.txt LIKE '%维生素%'
      OR t.txt LIKE '%补充剂%' OR t.txt LIKE '%益生菌%' OR t.txt LIKE '%鱼油%'
      OR t.txt LIKE '%胶囊%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '食品保健',
    category_level2 = '休闲食品',
    category_level3 = '零食/冲饮',
    category_path = '食品保健 > 休闲食品 > 零食/冲饮',
    category = '食品保健 > 休闲食品 > 零食/冲饮'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%coffee%' OR t.txt LIKE '%tea%' OR t.txt LIKE '%snack%'
      OR t.txt LIKE '%chocolate%' OR t.txt LIKE '%cookie%' OR t.txt LIKE '%almond butter%'
      OR t.txt LIKE '%零食%' OR t.txt LIKE '%咖啡%' OR t.txt LIKE '%茶%'
      OR t.txt LIKE '%巧克力%' OR t.txt LIKE '%饼干%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '图书音像',
    category_level2 = '图书',
    category_level3 = '文学/教育',
    category_path = '图书音像 > 图书 > 文学/教育',
    category = '图书音像 > 图书 > 文学/教育'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%book%' OR t.txt LIKE '%novel%' OR t.txt LIKE '%textbook%'
      OR t.txt LIKE '%kindle%' OR t.txt LIKE '%图书%' OR t.txt LIKE '%小说%'
      OR t.txt LIKE '%教材%' OR t.txt LIKE '%电子书%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '图书音像',
    category_level2 = '音像制品',
    category_level3 = 'CD/黑胶/影碟',
    category_path = '图书音像 > 音像制品 > CD/黑胶/影碟',
    category = '图书音像 > 音像制品 > CD/黑胶/影碟'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%vinyl%' OR t.txt LIKE '%album%' OR t.txt LIKE '%blu-ray%'
      OR t.txt LIKE '%唱片%' OR t.txt LIKE '%专辑%' OR t.txt LIKE '%蓝光%'
      OR (t.txt LIKE '%cd%' AND t.txt NOT LIKE '%cd player%')
  );

INSERT INTO search_alias_lexicon (alias, cluster_key, aliases, source, enabled)
VALUES
    ('碎纸机', '打印/装订/纸品', '打印机|碎纸机|活页夹|标签纸|printer|shredder|binder', 'MANUAL', true),
    ('printer', '打印/装订/纸品', '打印机|碎纸机|活页夹|标签纸|printer|shredder|binder', 'MANUAL', true),
    ('文具', '书写/本册', '文具|笔记本|中性笔|pen|notebook|marker', 'MANUAL', true),
    ('furniture', '客厅/卧室家具', '家具|沙发|床垫|furniture|sofa|mattress', 'MANUAL', true),
    ('lamp', '灯具照明', '灯具|台灯|照明|lamp|lighting|led light', 'MANUAL', true),
    ('baby', '喂养/出行/寝居', '婴儿|宝宝|婴童用品|baby|infant|stroller', 'MANUAL', true),
    ('toy', '益智/派对玩具', '玩具|拼图|积木|toy|puzzle|party', 'MANUAL', true),
    ('vitamin', '维矿/功能补充', '维生素|补充剂|vitamin|supplement|probiotic', 'MANUAL', true),
    ('snack', '零食/冲饮', '零食|咖啡|茶|snack|coffee|tea', 'MANUAL', true),
    ('book', '文学/教育', '图书|小说|教材|book|novel|textbook', 'MANUAL', true),
    ('vinyl', 'CD/黑胶/影碟', '唱片|专辑|黑胶|vinyl|album|cd', 'MANUAL', true)
ON CONFLICT (alias) DO NOTHING;

COMMIT;

-- ===== END patch_category_dense_coverage_v2.sql =====

-- ===== BEGIN patch_category_dense_coverage_v3.sql =====
BEGIN;

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '运动户外',
    category_level2 = '户外运动',
    category_level3 = '球类/骑行/垂钓',
    category_path = '运动户外 > 户外运动 > 球类/骑行/垂钓',
    category = '运动户外 > 户外运动 > 球类/骑行/垂钓'
FROM product_text t
WHERE p.id=t.id
  AND (
      t.txt LIKE '%racket%' OR t.txt LIKE '%golf%' OR t.txt LIKE '%fishing%'
      OR t.txt LIKE '%skateboard%' OR t.txt LIKE '%scooter%' OR t.txt LIKE '%tennis%'
      OR t.txt LIKE '%baseball%' OR t.txt LIKE '%网球%' OR t.txt LIKE '%高尔夫%'
      OR t.txt LIKE '%钓鱼%' OR t.txt LIKE '%滑板%' OR t.txt LIKE '%滑板车%'
      OR t.txt LIKE '%棒球%' OR t.txt LIKE '%运动头盔%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '时尚服饰',
    category_level2 = '珠宝配饰',
    category_level3 = '首饰/饰品',
    category_path = '时尚服饰 > 珠宝配饰 > 首饰/饰品',
    category = '时尚服饰 > 珠宝配饰 > 首饰/饰品'
FROM product_text t
WHERE p.id=t.id
  AND (
      t.txt LIKE '%bracelet%' OR t.txt LIKE '%necklace%' OR t.txt LIKE '%earring%'
      OR t.txt LIKE '%ring%' OR t.txt LIKE '%anklet%' OR t.txt LIKE '%手链%'
      OR t.txt LIKE '%项链%' OR t.txt LIKE '%耳环%' OR t.txt LIKE '%戒指%' OR t.txt LIKE '%脚链%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '美妆个护',
    category_level2 = '香氛个护',
    category_level3 = '香水/止汗',
    category_path = '美妆个护 > 香氛个护 > 香水/止汗',
    category = '美妆个护 > 香氛个护 > 香水/止汗'
FROM product_text t
WHERE p.id=t.id
  AND (
      t.txt LIKE '%perfume%' OR t.txt LIKE '%fragrance%' OR t.txt LIKE '%deodorant%'
      OR t.txt LIKE '%body spray%' OR t.txt LIKE '%香水%' OR t.txt LIKE '%止汗%'
      OR t.txt LIKE '%体香喷雾%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '家居生活',
    category_level2 = '园艺户外',
    category_level3 = '庭院/园艺用品',
    category_path = '家居生活 > 园艺户外 > 庭院/园艺用品',
    category = '家居生活 > 园艺户外 > 庭院/园艺用品'
FROM product_text t
WHERE p.id=t.id
  AND (
      t.txt LIKE '%garden%' OR t.txt LIKE '%patio%' OR t.txt LIKE '%trellis%'
      OR t.txt LIKE '%outdoor canopy%' OR t.txt LIKE '%plant%' OR t.txt LIKE '%花园%'
      OR t.txt LIKE '%庭院%' OR t.txt LIKE '%格架%' OR t.txt LIKE '%遮阳篷%'
      OR t.txt LIKE '%园艺%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '家居生活',
    category_level2 = '家装建材',
    category_level3 = '五金/电工/卫浴',
    category_path = '家居生活 > 家装建材 > 五金/电工/卫浴',
    category = '家居生活 > 家装建材 > 五金/电工/卫浴'
FROM product_text t
WHERE p.id=t.id
  AND (
      t.txt LIKE '%faucet%' OR t.txt LIKE '%shower valve%' OR t.txt LIKE '%door knob%'
      OR t.txt LIKE '%dimmer%' OR t.txt LIKE '%wire%' OR t.txt LIKE '%wiring%'
      OR t.txt LIKE '%connector%' OR t.txt LIKE '%水龙头%' OR t.txt LIKE '%淋浴阀%'
      OR t.txt LIKE '%门把手%' OR t.txt LIKE '%调光器%' OR t.txt LIKE '%接线%'
      OR t.txt LIKE '%连接器%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '汽车用品',
    category_level2 = '车船配件',
    category_level3 = '船舶/拖车配件',
    category_path = '汽车用品 > 车船配件 > 船舶/拖车配件',
    category = '汽车用品 > 车船配件 > 船舶/拖车配件'
FROM product_text t
WHERE p.id=t.id
  AND (
      t.txt LIKE '%boat%' OR t.txt LIKE '%dock line%' OR t.txt LIKE '%marine%'
      OR t.txt LIKE '%trailer%' OR t.txt LIKE '%船%' OR t.txt LIKE '%码头%'
      OR t.txt LIKE '%拖车%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '汽车用品',
    category_level2 = '汽车配件',
    category_level3 = '维修保养件',
    category_path = '汽车用品 > 汽车配件 > 维修保养件',
    category = '汽车用品 > 汽车配件 > 维修保养件'
FROM product_text t
WHERE p.id=t.id
  AND (
      t.txt LIKE '%oxygen sensor%' OR t.txt LIKE '%wiper%' OR t.txt LIKE '%fuel tank%'
      OR t.txt LIKE '%drive belt%' OR t.txt LIKE '%engine%' OR t.txt LIKE '%氧传感器%'
      OR t.txt LIKE '%雨刷%' OR t.txt LIKE '%油箱%' OR t.txt LIKE '%发动机%'
      OR t.txt LIKE '%皮带%'
  );

INSERT INTO search_alias_lexicon (alias, cluster_key, aliases, source, enabled)
VALUES
    ('fishing', '球类/骑行/垂钓', '钓鱼|网球|高尔夫|fishing|tennis|golf', 'MANUAL', true),
    ('necklace', '首饰/饰品', '项链|手链|耳环|necklace|bracelet|earring', 'MANUAL', true),
    ('perfume', '香水/止汗', '香水|止汗|体香喷雾|perfume|fragrance|deodorant', 'MANUAL', true),
    ('garden', '庭院/园艺用品', '花园|庭院|园艺|garden|patio|trellis', 'MANUAL', true),
    ('faucet', '五金/电工/卫浴', '水龙头|调光器|连接器|faucet|dimmer|connector', 'MANUAL', true),
    ('boat', '船舶/拖车配件', '船|拖车|码头|boat|trailer|dock line', 'MANUAL', true),
    ('oxygen sensor', '维修保养件', '氧传感器|雨刷|油箱|oxygen sensor|wiper|fuel tank', 'MANUAL', true)
ON CONFLICT (alias) DO NOTHING;

COMMIT;

-- ===== END patch_category_dense_coverage_v3.sql =====

-- ===== BEGIN patch_category_generic_reduction_v5.sql =====
BEGIN;

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '椋熷搧淇濆仴',
    category_level2 = '浼戦棽椋熷搧',
    category_level3 = '闆堕/鍐查ギ',
    category_path = '椋熷搧淇濆仴 > 浼戦棽椋熷搧 > 闆堕/鍐查ギ',
    category = '椋熷搧淇濆仴 > 浼戦棽椋熷搧 > 闆堕/鍐查ギ'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%barney butter%' OR g.txt LIKE '%almond butter%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '瀹跺眳鐢熸椿',
    category_level2 = '娓呮磥鐢ㄥ搧',
    category_level3 = '绾稿搧鑰楁潗',
    category_path = '瀹跺眳鐢熸椿 > 娓呮磥鐢ㄥ搧 > 绾稿搧鑰楁潗',
    category = '瀹跺眳鐢熸椿 > 娓呮磥鐢ㄥ搧 > 绾稿搧鑰楁潗'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%toilet paper%' OR g.txt LIKE '%鍗敓绾?' OR g.txt LIKE '%绾稿肪%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '鏁扮爜鐢靛瓙',
    category_level2 = '缃戠粶璁惧',
    category_level3 = '鏈烘煖/鏈烘灦',
    category_path = '鏁扮爜鐢靛瓙 > 缃戠粶璁惧 > 鏈烘煖/鏈烘灦',
    category = '鏁扮爜鐢靛瓙 > 缃戠粶璁惧 > 鏈烘煖/鏈烘灦'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%rack%' OR g.txt LIKE '%rack case%' OR g.txt LIKE '%network rack%' OR g.txt LIKE '%鏈烘灦%' OR g.txt LIKE '%鏈烘煖%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '鏁扮爜鐢靛瓙',
    category_level2 = '鏁扮爜閰嶄欢',
    category_level3 = '鐢垫簮/鎻掑骇',
    category_path = '鏁扮爜鐢靛瓙 > 鏁扮爜閰嶄欢 > 鐢垫簮/鎻掑骇',
    category = '鏁扮爜鐢靛瓙 > 鏁扮爜閰嶄欢 > 鐢垫簮/鎻掑骇'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%surge%' OR g.txt LIKE '%power tap%' OR g.txt LIKE '%鎻掓帓%' OR g.txt LIKE '%娴秾%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '鏁扮爜鐢靛瓙',
    category_level2 = '闊抽璁惧',
    category_level3 = '闊冲搷閰嶄欢',
    category_path = '鏁扮爜鐢靛瓙 > 闊抽璁惧 > 闊冲搷閰嶄欢',
    category = '鏁扮爜鐢靛瓙 > 闊抽璁惧 > 闊冲搷閰嶄欢'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%speaker%' OR g.txt LIKE '%audio isolator%' OR g.txt LIKE '%drum head%' OR g.txt LIKE '%鎵０鍣?' OR g.txt LIKE '%榧撶毊%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '姹借溅鐢ㄥ搧',
    category_level2 = '杞﹁浇閰嶄欢',
    category_level3 = '杞︾僵/閬槼',
    category_path = '姹借溅鐢ㄥ搧 > 杞﹁浇閰嶄欢 > 杞︾僵/閬槼',
    category = '姹借溅鐢ㄥ搧 > 杞﹁浇閰嶄欢 > 杞︾僵/閬槼'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%windshield%' OR g.txt LIKE '%sunshade%' OR g.txt LIKE '%ac cover%' OR g.txt LIKE '%绌鸿皟缃?' OR g.txt LIKE '%闃叉檼鎸?');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '瀹跺眳鐢熸椿',
    category_level2 = '瀹惰寤烘潗',
    category_level3 = '鍗荡/鍦伴潰鏉愭枡',
    category_path = '瀹跺眳鐢熸椿 > 瀹惰寤烘潗 > 鍗荡/鍦伴潰鏉愭枡',
    category = '瀹跺眳鐢熸椿 > 瀹惰寤烘潗 > 鍗荡/鍦伴潰鏉愭枡'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%shower hose%' OR g.txt LIKE '%flange%' OR g.txt LIKE '%tile%' OR g.txt LIKE '%娣嬫荡杞%' OR g.txt LIKE '%娉曞叞%' OR g.txt LIKE '%鍦扮爾%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '瀹跺眳鐢熸椿',
    category_level2 = '鍘ㄦ埧鐢ㄥ搧',
    category_level3 = '鍒€鍏?纾ㄥ垁',
    category_path = '瀹跺眳鐢熸椿 > 鍘ㄦ埧鐢ㄥ搧 > 鍒€鍏?纾ㄥ垁',
    category = '瀹跺眳鐢熸椿 > 鍘ㄦ埧鐢ㄥ搧 > 鍒€鍏?纾ㄥ垁'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%sharpener%' OR g.txt LIKE '%knife%' OR g.txt LIKE '%纾ㄥ垁%' OR g.txt LIKE '%灏忓垁%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '宸ヤ笟鐢ㄥ搧',
    category_level2 = '鍖栧伐杈呮枡',
    category_level3 = '鑳堕粡/淇ˉ鏉愭枡',
    category_path = '宸ヤ笟鐢ㄥ搧 > 鍖栧伐杈呮枡 > 鑳堕粡/淇ˉ鏉愭枡',
    category = '宸ヤ笟鐢ㄥ搧 > 鍖栧伐杈呮枡 > 鑳堕粡/淇ˉ鏉愭枡'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%adhesive%' OR g.txt LIKE '%resin finish%' OR g.txt LIKE '%wood filler%' OR g.txt LIKE '%鑳剁矘鍓?' OR g.txt LIKE '%鏍戣剛%' OR g.txt LIKE '%濉枡%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '鏃跺皻鏈嶉グ',
    category_level2 = '閰嶉グ',
    category_level3 = '甯藉瓙/鍋囧彂',
    category_path = '鏃跺皻鏈嶉グ > 閰嶉グ > 甯藉瓙/鍋囧彂',
    category = '鏃跺皻鏈嶉グ > 閰嶉グ > 甯藉瓙/鍋囧彂'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%wig%' OR g.txt LIKE '%hat%' OR g.txt LIKE '%鍋囧彂%' OR g.txt LIKE '%甯藉瓙%' OR g.txt LIKE '%costumes%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '鏃跺皻鏈嶉グ',
    category_level2 = '鏈嶈',
    category_level3 = '琚滃瓙',
    category_path = '鏃跺皻鏈嶉グ > 鏈嶈 > 琚滃瓙',
    category = '鏃跺皻鏈嶉グ > 鏈嶈 > 琚滃瓙'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%sock%' OR g.txt LIKE '%闅愯棌杞粨%');

WITH product_text AS (
    SELECT id, lower(coalesce(name,'') || ' ' || coalesce(brand,'') || ' ' || coalesce(description,'')) AS txt
    FROM products
),
generic_products AS (
    SELECT p.id, t.txt
    FROM products p
    JOIN product_text t ON t.id = p.id
    WHERE p.category_level3 = '閫氱敤鍟嗗搧' OR p.category_level2 = '閫氱敤鍟嗗搧' OR p.category_level1 = '缁煎悎'
)
UPDATE products p
SET
    category_level1 = '鍔炲叕鏂囧叿',
    category_level2 = '鍔炲叕璁惧',
    category_level3 = '鎵撳嵃/瑁呰/绾稿搧',
    category_path = '鍔炲叕鏂囧叿 > 鍔炲叕璁惧 > 鎵撳嵃/瑁呰/绾稿搧',
    category = '鍔炲叕鏂囧叿 > 鍔炲叕璁惧 > 鎵撳嵃/瑁呰/绾稿搧'
FROM generic_products g
WHERE p.id = g.id
  AND (g.txt LIKE '%sheet protector%' OR g.txt LIKE '%recipe card%' OR g.txt LIKE '%鑳跺甫%' OR g.txt LIKE '%鏍囩%');

INSERT INTO search_alias_lexicon (alias, cluster_key, aliases, source, enabled)
VALUES
    ('network rack', '鏈烘煖/鏈烘灦', 'network rack|rack case|鏈烘灦|鏈烘煖', 'MANUAL', true),
    ('surge protector', '鐢垫簮/鎻掑骇', 'surge protector|power tap|鎻掓帓|娴秾淇濇姢', 'MANUAL', true),
    ('shower hose', '鍗荡/鍦伴潰鏉愭枡', 'shower hose|flange|tile|娣嬫荡杞|娉曞叞|鍦扮爾', 'MANUAL', true),
    ('wig', '甯藉瓙/鍋囧彂', 'wig|hat|鍋囧彂|甯藉瓙', 'MANUAL', true)
ON CONFLICT (alias) DO NOTHING;

COMMIT;
-- ===== END patch_category_generic_reduction_v5.sql =====

-- ===== BEGIN patch_reclassify_product_categories.sql =====
BEGIN;

WITH product_text AS (
    SELECT
        id,
        lower(
            coalesce(name, '') || ' ' ||
            coalesce(brand, '') || ' ' ||
            coalesce(description, '')
        ) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '数码电子',
    category_level2 = '电脑外设',
    category_level3 = '鼠标',
    category_path = '数码电子 > 电脑外设 > 鼠标',
    category = '数码电子 > 电脑外设 > 鼠标'
FROM product_text t
WHERE p.id = t.id
  AND (t.txt LIKE '%mouse%' OR t.txt LIKE '%鼠标%');

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '数码电子',
    category_level2 = '电脑外设',
    category_level3 = '键盘',
    category_path = '数码电子 > 电脑外设 > 键盘',
    category = '数码电子 > 电脑外设 > 键盘'
FROM product_text t
WHERE p.id = t.id
  AND (t.txt LIKE '%keyboard%' OR t.txt LIKE '%键盘%' OR t.txt LIKE '%机械键盘%');

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '数码电子',
    category_level2 = '电脑外设',
    category_level3 = '显示器',
    category_path = '数码电子 > 电脑外设 > 显示器',
    category = '数码电子 > 电脑外设 > 显示器'
FROM product_text t
WHERE p.id = t.id
  AND (t.txt LIKE '%monitor%' OR t.txt LIKE '%gaming monitor%' OR t.txt LIKE '%显示器%');

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '家居生活',
    category_level2 = '家纺床品',
    category_level3 = '被子/床上用品',
    category_path = '家居生活 > 家纺床品 > 被子/床上用品',
    category = '家居生活 > 家纺床品 > 被子/床上用品'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%quilt%' OR t.txt LIKE '%duvet%' OR t.txt LIKE '%comforter%' OR t.txt LIKE '%blanket%'
      OR t.txt LIKE '%bedding%' OR t.txt LIKE '%被子%' OR t.txt LIKE '%棉被%' OR t.txt LIKE '%羽绒被%'
      OR t.txt LIKE '%床品%' OR t.txt LIKE '%四件套%' OR t.txt LIKE '%被套%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '数码电子',
    category_level2 = '数码配件',
    category_level3 = '扩展坞/切换器',
    category_path = '数码电子 > 数码配件 > 扩展坞/切换器',
    category = '数码电子 > 数码配件 > 扩展坞/切换器'
FROM product_text t
WHERE p.id = t.id
  AND (t.txt LIKE '%kvm%' OR t.txt LIKE '%switcher%' OR t.txt LIKE '%docking station%' OR t.txt LIKE '%usb hub%' OR t.txt LIKE '%扩展坞%' OR t.txt LIKE '%切换器%');

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '游戏娱乐',
    category_level2 = '电子游戏',
    category_level3 = CASE
        WHEN (t.txt LIKE '%controller%' OR t.txt LIKE '%gamepad%' OR t.txt LIKE '%joystick%' OR t.txt LIKE '%手柄%' OR t.txt LIKE '%摇杆%')
            THEN '游戏外设'
        WHEN (t.txt LIKE '%game card%' OR t.txt LIKE '%game cd%' OR t.txt LIKE '%video game%' OR t.txt LIKE '%游戏光盘%' OR t.txt LIKE '%游戏卡%')
            THEN '游戏软件'
        ELSE '游戏主机'
    END,
    category_path = CASE
        WHEN (t.txt LIKE '%controller%' OR t.txt LIKE '%gamepad%' OR t.txt LIKE '%joystick%' OR t.txt LIKE '%手柄%' OR t.txt LIKE '%摇杆%')
            THEN '游戏娱乐 > 电子游戏 > 游戏外设'
        WHEN (t.txt LIKE '%game card%' OR t.txt LIKE '%game cd%' OR t.txt LIKE '%video game%' OR t.txt LIKE '%游戏光盘%' OR t.txt LIKE '%游戏卡%')
            THEN '游戏娱乐 > 电子游戏 > 游戏软件'
        ELSE '游戏娱乐 > 电子游戏 > 游戏主机'
    END,
    category = CASE
        WHEN (t.txt LIKE '%controller%' OR t.txt LIKE '%gamepad%' OR t.txt LIKE '%joystick%' OR t.txt LIKE '%手柄%' OR t.txt LIKE '%摇杆%')
            THEN '游戏娱乐 > 电子游戏 > 游戏外设'
        WHEN (t.txt LIKE '%game card%' OR t.txt LIKE '%game cd%' OR t.txt LIKE '%video game%' OR t.txt LIKE '%游戏光盘%' OR t.txt LIKE '%游戏卡%')
            THEN '游戏娱乐 > 电子游戏 > 游戏软件'
        ELSE '游戏娱乐 > 电子游戏 > 游戏主机'
    END
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%playstation%' OR t.txt LIKE '%ps5%' OR t.txt LIKE '%ps4%' OR t.txt LIKE '%xbox%'
      OR t.txt LIKE '%nintendo switch%' OR t.txt LIKE '%steam deck%'
      OR t.txt LIKE '%game console%' OR t.txt LIKE '%游戏机%' OR t.txt LIKE '%游戏主机%'
      OR t.txt LIKE '%controller%' OR t.txt LIKE '%gamepad%' OR t.txt LIKE '%joystick%'
      OR t.txt LIKE '%电子游戏%'
  )
  AND NOT (
      t.txt LIKE '%kvm%' OR t.txt LIKE '%switcher%' OR t.txt LIKE '%docking station%' OR t.txt LIKE '%usb hub%'
      OR t.txt LIKE '%扩展坞%' OR t.txt LIKE '%切换器%'
  );

WITH product_text AS (
    SELECT id, lower(coalesce(name, '') || ' ' || coalesce(brand, '') || ' ' || coalesce(description, '')) AS txt
    FROM products
)
UPDATE products p
SET
    category_level1 = '数码电子',
    category_level2 = '电脑整机',
    category_level3 = '笔记本/台式机',
    category_path = '数码电子 > 电脑整机 > 笔记本/台式机',
    category = '数码电子 > 电脑整机 > 笔记本/台式机'
FROM product_text t
WHERE p.id = t.id
  AND (
      t.txt LIKE '%laptop%' OR t.txt LIKE '%notebook%' OR t.txt LIKE '%macbook%'
      OR t.txt LIKE '%desktop%' OR t.txt LIKE '%all in one%' OR t.txt LIKE '%mini pc%'
      OR t.txt LIKE '%computer%' OR t.txt LIKE '%笔记本%' OR t.txt LIKE '%台式%' OR t.txt LIKE '%电脑主机%'
  )
  AND NOT (
      t.txt LIKE '%mouse%' OR t.txt LIKE '%keyboard%' OR t.txt LIKE '%monitor%' OR t.txt LIKE '%display%'
      OR t.txt LIKE '%charger%' OR t.txt LIKE '%cable%' OR t.txt LIKE '%鼠标%' OR t.txt LIKE '%键盘%'
      OR t.txt LIKE '%显示器%' OR t.txt LIKE '%充电器%' OR t.txt LIKE '%数据线%'
  );

-- Keep alias lexicon in sync for new high-priority clusters.
INSERT INTO search_alias_lexicon (alias, cluster_key, aliases, source, enabled)
VALUES
    ('mouse', '鼠标', '鼠标|mouse|wireless mouse|gaming mouse', 'MANUAL', true),
    ('无线鼠标', '鼠标', '鼠标|mouse|wireless mouse|gaming mouse', 'MANUAL', true),
    ('keyboard', '键盘', '键盘|keyboard|mechanical keyboard|gaming keyboard', 'MANUAL', true),
    ('monitor', '显示器', '显示器|monitor|display|screen', 'MANUAL', true),
    ('kvm', '扩展坞/切换器', 'kvm|switcher|docking station|usb hub|扩展坞|切换器', 'MANUAL', true),
    ('被子', '被子/床上用品', '被子|棉被|床品|duvet|comforter|blanket', 'MANUAL', true),
    ('duvet', '被子/床上用品', '被子|棉被|床品|duvet|comforter|blanket', 'MANUAL', true),
    ('游戏机', '游戏主机', '游戏机|游戏主机|playstation|xbox|nintendo switch', 'MANUAL', true),
    ('playstation', '游戏主机', '游戏机|游戏主机|playstation|xbox|nintendo switch', 'MANUAL', true),
    ('手柄', '游戏外设', '手柄|controller|gamepad|joystick', 'MANUAL', true),
    ('电子游戏', '游戏软件', '电子游戏|video game|game cd|game card', 'MANUAL', true)
ON CONFLICT (alias) DO NOTHING;

COMMIT;

-- ===== END patch_reclassify_product_categories.sql =====

-- ===== BEGIN patch_category_quality_guardrails_v8.sql =====
BEGIN;

-- v8: category quality guardrails (ASCII-safe).
-- This patch is designed to be repeatable and avoids id-based one-off edits.

WITH product_text AS (
    SELECT
        id,
        lower(
            coalesce(name, '') || ' ' ||
            coalesce(brand, '') || ' ' ||
            coalesce(description, '')
        ) AS txt
    FROM products
),
updated AS (
    UPDATE products p
    SET
        category_level1 = 'Electronics',
        category_level2 = 'Audio',
        category_level3 = 'Headphones',
        category_path = 'Electronics > Audio > Headphones',
        category = 'Electronics > Audio > Headphones'
    FROM product_text t
    WHERE p.id = t.id
      AND t.txt ~* '(headphone|earbud|earbuds|headset|bluetooth *ear|tws|wireless *ear)'
      AND t.txt !~* '(phone *case|screen *protector|charging *cable|usb *cable|charger)'
      AND p.category_path <> 'Electronics > Audio > Headphones'
    RETURNING p.id
)
SELECT count(*) AS fixed_headphones FROM updated;

WITH product_text AS (
    SELECT
        id,
        lower(
            coalesce(name, '') || ' ' ||
            coalesce(brand, '') || ' ' ||
            coalesce(description, '')
        ) AS txt
    FROM products
),
updated AS (
    UPDATE products p
    SET
        category_level1 = 'Electronics',
        category_level2 = 'Camera',
        category_level3 = 'Digital Camera',
        category_path = 'Electronics > Camera > Digital Camera',
        category = 'Electronics > Camera > Digital Camera'
    FROM product_text t
    WHERE p.id = t.id
      AND t.txt ~* '(ccd|camera|digital *camera|camcorder)'
      AND t.txt !~* '(game *cd|music *cd|compact *disc|dvd|blu *-?ray|vinyl|record)'
      AND p.category_path <> 'Electronics > Camera > Digital Camera'
    RETURNING p.id
)
SELECT count(*) AS fixed_cameras FROM updated;

WITH product_text AS (
    SELECT
        id,
        lower(
            coalesce(name, '') || ' ' ||
            coalesce(brand, '') || ' ' ||
            coalesce(description, '')
        ) AS txt
    FROM products
),
updated AS (
    UPDATE products p
    SET
        category_level1 = 'Fashion',
        category_level2 = 'Accessories',
        category_level3 = 'Belts',
        category_path = 'Fashion > Accessories > Belts',
        category = 'Fashion > Accessories > Belts'
    FROM product_text t
    WHERE p.id = t.id
      AND t.txt ~* '(belt|leather *belt|waist *belt)'
      AND p.category_path <> 'Fashion > Accessories > Belts'
    RETURNING p.id
)
SELECT count(*) AS fixed_belts FROM updated;

WITH product_text AS (
    SELECT
        id,
        lower(
            coalesce(name, '') || ' ' ||
            coalesce(brand, '') || ' ' ||
            coalesce(description, '')
        ) AS txt
    FROM products
),
updated AS (
    UPDATE products p
    SET
        category_level1 = 'Home Appliances',
        category_level2 = 'Kitchen Appliances',
        category_level3 = 'Coffee and Ice Machines',
        category_path = 'Home Appliances > Kitchen Appliances > Coffee and Ice Machines',
        category = 'Home Appliances > Kitchen Appliances > Coffee and Ice Machines'
    FROM product_text t
    WHERE p.id = t.id
      AND t.txt ~* '(ice *maker|espresso|coffee *maker|coffee *machine)'
      AND p.category_path <> 'Home Appliances > Kitchen Appliances > Coffee and Ice Machines'
    RETURNING p.id
)
SELECT count(*) AS fixed_kitchen_machines FROM updated;

-- Keep category fields self-consistent after rule updates.
UPDATE products
SET
    category_path = category_level1 || ' > ' || category_level2 || ' > ' || category_level3,
    category = category_level1 || ' > ' || category_level2 || ' > ' || category_level3
WHERE
    category_level1 IS NOT NULL
    AND category_level2 IS NOT NULL
    AND category_level3 IS NOT NULL
    AND (
        category_path IS DISTINCT FROM category_level1 || ' > ' || category_level2 || ' > ' || category_level3
        OR category IS DISTINCT FROM category_level1 || ' > ' || category_level2 || ' > ' || category_level3
    );

COMMIT;
-- ===== END patch_category_quality_guardrails_v8.sql =====
