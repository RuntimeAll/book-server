-- PRD-C-205 · 课件文档表：一个课时一份 Umo/Tiptap 文档 JSON（数据驱动，编辑即改数据）
-- C 线预留段 V9xx（见 book-ai/CLAUDE.md §3.1）；IF NOT EXISTS 保证与 dev 库 pymysql 预建幂等。
CREATE TABLE IF NOT EXISTS biz_kg_doc (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  course_id    VARCHAR(20)  NOT NULL COMMENT '课时节点 id（biz_subject L4）',
  book_id      VARCHAR(32)  DEFAULT NULL COMMENT '教辅书 id，如 CC7S（崔崔讲义七上）',
  title        VARCHAR(200) DEFAULT NULL COMMENT '课件标题',
  doc_json     LONGTEXT     COMMENT 'Umo/Tiptap 文档 JSON（整份课件；讲义大纲=heading，自定义节点 example(qid)/callout/mindmap）',
  status       CHAR(1)      DEFAULT '0' COMMENT '状态：0=正常',
  source       VARCHAR(32)  DEFAULT NULL COMMENT '来源',
  source_book  VARCHAR(64)  DEFAULT NULL COMMENT '来源书名',
  create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_book (course_id, book_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课件文档（一课时一份 Tiptap JSON，PRD-C-205）';
