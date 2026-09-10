package com.eventverse.app.infrastructure.tables

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.json.jsonb

/**
 * Declares a PostgreSQL `JSONB` column whose value is handled as raw JSON text.
 *
 * The schema migrations define `graph_data`, `module_permissions` and friends as `JSONB`,
 * but the Exposed tables previously declared them as `text()`. The JDBC driver then sends a
 * `varchar` parameter, and PostgreSQL rejects it:
 *
 *     column "graph_data" is of type jsonb but expression is of type character varying
 *
 * Binding the column as `jsonb` makes the driver send a properly typed value. Serialisation
 * stays the caller's job — the domain-level codecs already produce and consume JSON text —
 * so the mapping here is deliberately the identity function.
 */
fun Table.jsonbText(name: String): Column<String> =
    jsonb(name, serialize = { it }, deserialize = { it })
