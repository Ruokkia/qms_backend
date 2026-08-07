# Flyway database migrations

The application executes database migrations automatically when it starts.
The canonical migrations are in `src/main/resources/db/migration/`:

- `V20260727161504201__baseline_schema.sql` creates the complete `qms` schema.
- `V20260727161504202__reference_seed_data.sql` inserts the reference, account, permission, and seed data.

For a new environment, create an empty PostgreSQL database and start the
backend. Flyway creates the `qms` schema, applies V1 and V2, and records the
result in `qms.flyway_schema_history`.

Do not edit a migration after it has been executed in a shared environment.
Every later schema or seed change must be added as a new versioned file using
the creation timestamp in `yyyyMMddHHmmssSSS` format, for example
`V20260728101530123__add_supplier_level.sql`. This prevents version conflicts
when several developers create migrations in parallel.

`db/legacy-seed/` is historical reference material and is not run by Flyway.
The canonical database definition is exclusively the versioned scripts under
`src/main/resources/db/migration/`.

An existing database without `qms.flyway_schema_history` is baselined at
`20260727161504202` on first startup. It must already match the current
baseline structure; otherwise
restore an approved backup or rebuild it before startup. Baseline does not
modify existing tables or data.

## Migration Iron Law (HARD RULE)

Migration scripts (files under `db/migrations/`) MUST ONLY:

- perform **structural changes**: `CREATE TABLE`, `ALTER TABLE ... ADD COLUMN`,
  `CREATE INDEX`, change column type, add constraint;
- insert **new initial / seed data** (`INSERT` of brand-new rows).

Migration scripts MUST NEVER contain any of the following, because they ship to
production with every release and can wipe / mutate production data with no easy
rollback, and they also break ISO13485 electronic-record traceability:

- `DELETE` (including `DELETE FROM`, `ON DELETE` FK actions are fine)
- `UPDATE` (even "logical delete" `SET is_deleted = 1`)
- `TRUNCATE`
- `DROP` (`DROP TABLE` / `DROP COLUMN` / `DROP INDEX`)

### Enforcement (soft rule -> hard gate)

A pre-commit / CI check script `db/check-migration-safety.ps1` scans every
`db/migrations/*.sql` for the forbidden keywords above (case-insensitive,
ignoring `--` comments and quoted literals, and excluding `ON DELETE`).

- Any **new** script that matches fails the check (non-zero exit) and blocks the
  commit / pipeline.
- A fixed `$LegacyAllowlist` of already-applied historical scripts that violate
  this law only emits a warning (tech debt to be cleaned up later), so the
  repository stays green.

Run it locally before committing:

```powershell
powershell -File db/check-migration-safety.ps1
```

If you genuinely need to clear / mutate / reset data (e.g. fix dirty production
data), do NOT put it in a Flyway migration. Use a separate, non-Flyway ops script
under `scripts/` or run it manually in a dev / approved environment only.
