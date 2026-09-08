-- Reconcile public paper grade/volume categories from active bookshelf curricula.
-- Scope: additive reference data only. Existing papers are not updated or backfilled.
-- Reserved generated key format: 39 + subject(2) + stage(2) + grade(2) + volume(2).
-- Public paper root 3001 is the stable domain key used by the paper-library API.

START TRANSACTION;

INSERT INTO biz_paper_category
    (id, parent_id, name, sort, subject, stage, grade, volume, paper_type, node_kind)
SELECT curriculum.generated_id,
       '3001',
       CONCAT(grade_dict.dict_label, COALESCE(volume_dict.dict_label, '')),
       curriculum.sort_value,
       curriculum.subject,
       curriculum.stage,
       curriculum.grade,
       curriculum.volume,
       NULL,
       'grade'
FROM (
    SELECT DISTINCT
           subject.subject,
           subject.stage,
           subject.grade,
           subject.volume,
           CONCAT(
               '39',
               LPAD(subject.subject, 2, '0'),
               LPAD(subject.stage, 2, '0'),
               LPAD(subject.grade, 2, '0'),
               LPAD(COALESCE(subject.volume, 0), 2, '0')
           ) AS generated_id,
           subject.subject * 100000 + subject.stage * 10000
               + subject.grade * 10 + COALESCE(subject.volume, 0) AS sort_value
    FROM biz_shelf_book book
    JOIN biz_subject subject ON subject.id = book.subject_id
    WHERE book.status = '0'
      AND subject.status = '0'
      AND subject.subject IS NOT NULL
      AND subject.stage IS NOT NULL
      AND subject.grade IS NOT NULL
) curriculum
JOIN sys_dict_data grade_dict
 ON grade_dict.tenant_id = '000000'
 AND grade_dict.dict_type = 'biz_edu_grade'
 AND CAST(grade_dict.dict_value AS UNSIGNED) = curriculum.grade
LEFT JOIN sys_dict_data volume_dict
 ON volume_dict.tenant_id = '000000'
 AND volume_dict.dict_type = 'biz_edu_volume'
 AND CAST(volume_dict.dict_value AS UNSIGNED) = curriculum.volume
WHERE NOT EXISTS (
    SELECT 1
    FROM biz_paper_category existing
    WHERE existing.parent_id = '3001'
      AND existing.node_kind = 'grade'
      AND existing.subject = curriculum.subject
      AND existing.stage = curriculum.stage
      AND existing.grade = curriculum.grade
      AND existing.volume <=> curriculum.volume
)
AND NOT EXISTS (
    SELECT 1 FROM biz_paper_category collision WHERE collision.id = curriculum.generated_id
);

INSERT INTO biz_paper_category
    (id, parent_id, name, sort, subject, stage, grade, volume, paper_type, node_kind)
SELECT CONCAT(public_grade.id, LPAD(CAST(CAST(paper_type.dict_value AS UNSIGNED) AS CHAR), 2, '0')),
       public_grade.id,
       paper_type.dict_label,
       CAST(paper_type.dict_value AS UNSIGNED),
       public_grade.subject,
       public_grade.stage,
       public_grade.grade,
       public_grade.volume,
       CAST(paper_type.dict_value AS UNSIGNED),
       'ptype'
FROM biz_paper_category public_grade
JOIN (
    SELECT DISTINCT subject.subject, subject.stage, subject.grade, subject.volume
    FROM biz_shelf_book book
    JOIN biz_subject subject ON subject.id = book.subject_id
    WHERE book.status = '0'
      AND subject.status = '0'
      AND subject.subject IS NOT NULL
      AND subject.stage IS NOT NULL
      AND subject.grade IS NOT NULL
) curriculum
  ON curriculum.subject = public_grade.subject
 AND curriculum.stage = public_grade.stage
 AND curriculum.grade = public_grade.grade
 AND curriculum.volume <=> public_grade.volume
JOIN sys_dict_data paper_type
 ON paper_type.tenant_id = '000000'
 AND paper_type.dict_type = 'biz_paper_type'
WHERE public_grade.parent_id = '3001'
  AND public_grade.node_kind = 'grade'
  AND NOT EXISTS (
      SELECT 1
      FROM biz_paper_category existing
      WHERE existing.parent_id = public_grade.id
        AND existing.node_kind = 'ptype'
        AND existing.paper_type = CAST(paper_type.dict_value AS UNSIGNED)
  )
  AND NOT EXISTS (
      SELECT 1
      FROM biz_paper_category collision
      WHERE collision.id = CONCAT(
          public_grade.id,
          LPAD(CAST(CAST(paper_type.dict_value AS UNSIGNED) AS CHAR), 2, '0')
      )
  );

COMMIT;
