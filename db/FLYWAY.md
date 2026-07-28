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
