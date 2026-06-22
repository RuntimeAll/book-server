-- PRD-A-021 R3b: 章节 × 图型 映射表
-- 举一反三造图定型闸：toolkit 直读本表，按母题所属章节名/考点名「包含匹配」chapter_keyword，
-- 取得该章节允许的 figure_type 集合，再约束造图链翻命令（治"数轴乱画/图型有限"）。
-- 绑定粒度 = 章节（用户拍板，不下沉考点叶子）。多对多：一 keyword 多图型、一图型多 keyword。
-- 纯增量（CREATE + seed），不改历史。

CREATE TABLE `biz_chapter_figure_map` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `chapter_keyword` varchar(64) NOT NULL COMMENT '章节/考点主题关键词（toolkit 用母题章节名/考点名包含匹配它）',
  `figure_type` varchar(32) NOT NULL COMMENT '图型 code（英文 code，不翻译）',
  `note` varchar(128) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (`id`),
  KEY `idx_chapter_keyword` (`chapter_keyword`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='章节×图型映射（举一反三造图定型闸，PRD-A-021 R3b）';

-- ============ seed（附录A 映射）============
INSERT INTO `biz_chapter_figure_map` (`chapter_keyword`, `figure_type`) VALUES
-- 数与式 / 不等式 → 数轴
('有理数', 'number_line'),
('实数', 'number_line'),
('绝对值', 'number_line'),
('不等式', 'number_line'),
-- 坐标与函数
('平面直角坐标系', 'cartesian'),
('坐标', 'cartesian'),
('一次函数', 'cartesian'),
('一次函数', 'line_func'),
('二次函数', 'cartesian'),
('二次函数', 'parabola'),
('反比例函数', 'cartesian'),
('反比例函数', 'hyperbola'),
-- 线与角
('相交线', 'angles_lines'),
('平行线', 'angles_lines'),
-- 三角形
('三角形', 'triangle'),
('全等', 'triangle'),
('全等', 'congruent_pair'),
('相似', 'triangle'),
('相似', 'congruent_pair'),
('勾股', 'triangle'),
('直角三角形', 'triangle'),
('等腰', 'triangle'),
('等边', 'triangle'),
('锐角三角函数', 'triangle'),
('解直角三角形', 'triangle'),
-- 四边形
('四边形', 'quadrilateral'),
('平行四边形', 'quadrilateral'),
('矩形', 'quadrilateral'),
('菱形', 'quadrilateral'),
('正方形', 'quadrilateral'),
('梯形', 'quadrilateral'),
-- 圆
('圆', 'circle'),
('圆周角', 'circle'),
('切线', 'circle'),
('扇形', 'circle'),
-- 图形变换
('平移', 'transform'),
('旋转', 'transform'),
('轴对称', 'transform'),
('中心对称', 'transform'),
-- 立体 / 投影
('立体图形', 'solid'),
('三视图', 'solid'),
('展开图', 'solid'),
-- 统计
('统计', 'stat_chart'),
('统计图', 'stat_chart'),
-- 尺规作图
('尺规作图', 'ruler_compass');
