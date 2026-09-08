# PRD-028 Deployment Prerequisites

Scope: lecture reader pagination, persisted question basket, paper snapshots,
idempotent paper creation, paper management and publication visibility.

## Release Order

1. Back up the target database. Inspect existing tables and columns against
   `schema.sql`; it is a manually reviewed schema change, not a Flyway migration.
2. Apply missing schema changes before starting the new backend. CREATE TABLE
   IF NOT EXISTS does not validate an existing table's shape. The ADD COLUMN
   statement must not be rerun if those columns already exist.
3. Deploy the matching PRD-028 backend and frontend revisions together. The new
   frontend needs the new basket/reader APIs and server-side permission fields.
4. Verify lecture opening, basket persistence after refresh, paper creation,
   edit/readback, public-library discovery, PDF preview, and publication toggles.
   Also test an ordinary teacher and an anonymous reader against a hidden paper.

No historical rows are migrated, backfilled or deleted. New paper instances
store snapshots; old rows continue using the legacy read path. Publication
reuses existing paper status values and requires no additional schema changes.

New papers without an explicit category inherit a unique grade/volume category
from the selected source books' curriculum, falling back to the question curriculum
when no book curriculum is specified. Matching uses structured taxonomy dimensions
and parent relations, not ID prefixes or title parsing. Mixed or incomplete curricula
remain unclassified rather than being assigned a guessed grade. A complete curriculum
whose public category is missing or duplicated is rejected instead of silently creating
an unclassified paper.

Before deployment, run `public-paper-category-reconcile.sql` after the verified backup.
It additively reconciles public grade/volume and paper-type reference nodes from active
bookshelf curricula and system dictionaries. The generated IDs use a reserved,
dimension-based numeric namespace; titles come from dictionaries. Existing paper rows
are not backfilled, updated or deleted. The correction requires no table DDL.

## Rollback

Roll frontend and backend code back as a matching pair. Keep the additive tables,
columns and widened score field. Do not drop data to roll back code. Independently
assess old-code visibility behavior if any papers have been unpublished since
release. Previously downloaded copies and historical export files are not revoked.

## Local Verification

Before this commit: 78 focused Java tests, 25 frontend isolated tests and 8
publication/category page/API scenarios passed. Backend install and frontend
production build passed. This is not a claim that every repository test or every
product module was tested. No production deployment or production DDL was run.

Grade-inheritance follow-up: 87 focused Java tests and 6 real page/API scenarios
passed, including create, retry, edit/readback, grade-filtered discovery, leaf-node
ancestry and mixed grades. The backend install passed; no frontend code changed.

Java focused test command:

```text
mvn -q -pl ruoyi-modules/ruoyi-book -am -DskipTests=false -Dgroups=dev -Dtest=Paper*Test,SelectionRulesTest,Question*Test,BasketPaperSerializationTest,Shelf*Test,MaterialValidationAdviceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

UI evidence and local regression harnesses remain in the workspace PRD-028
acceptance directory; they are not uploaded as database dumps or browser sessions.
