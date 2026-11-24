package com.autu.lio.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ScriptSqlPolicyTest {
    @Test
    fun acceptsSelectAndCountsOnlyUnquotedPositionalParameters() {
        val result = ScriptSqlPolicy.validate(
            "SELECT * FROM users WHERE name = ? AND note = '?' AND id > ?",
            listOf("LIO", 10),
            ScriptSqlPolicy.Mode.QUERY
        )
        assertEquals("SELECT", result.verb)
        assertEquals(2, result.arguments.size)
        assertEquals("LIO", result.arguments[0])
        assertEquals(10L, result.arguments[1])
        assertEquals("10", result.queryArguments[1])
    }

    @Test
    fun acceptsBoundMutationAndSafeSchemaStatements() {
        val create = ScriptSqlPolicy.validate(
            "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, name TEXT)",
            emptyList<Any?>(),
            ScriptSqlPolicy.Mode.EXECUTE
        )
        val insert = ScriptSqlPolicy.validate(
            "INSERT INTO users(name) VALUES (?)",
            listOf("LIO"),
            ScriptSqlPolicy.Mode.EXECUTE
        )
        val update = ScriptSqlPolicy.validate(
            "UPDATE users SET name = ? WHERE id = ?",
            listOf("AUTU", 1),
            ScriptSqlPolicy.Mode.EXECUTE
        )
        assertEquals("CREATE", create.verb)
        assertEquals("INSERT", insert.verb)
        assertEquals("UPDATE", update.verb)
    }

    @Test
    fun normalizesScalarTypesForSafeBinding() {
        val result = ScriptSqlPolicy.validate(
            "INSERT INTO values_table(a,b,c,d) VALUES (?,?,?,?)",
            listOf("text", 12, 2.5, true),
            ScriptSqlPolicy.Mode.EXECUTE
        )
        assertEquals("text", result.arguments[0])
        assertEquals(12L, result.arguments[1])
        assertEquals(2.5, result.arguments[2])
        assertEquals(1L, result.arguments[3])
        assertNull(ScriptSqlPolicy.validate(
            "SELECT * FROM values_table WHERE a = ?",
            listOf(null),
            ScriptSqlPolicy.Mode.QUERY
        ).queryArguments[0])
    }

    @Test
    fun rejectsMutationInQueryModeAndReadOnlyStatementInExecuteMode() {
        assertFalse(runCatching {
            ScriptSqlPolicy.validate("DELETE FROM users", emptyList<Any?>(), ScriptSqlPolicy.Mode.QUERY)
        }.isSuccess)
        assertFalse(runCatching {
            ScriptSqlPolicy.validate("SELECT * FROM users", emptyList<Any?>(), ScriptSqlPolicy.Mode.EXECUTE)
        }.isSuccess)
    }

    @Test
    fun rejectsDangerousCommandsAndSchemaFeatures() {
        val forbidden = listOf(
            "PRAGMA user_version",
            "ATTACH DATABASE 'other.db' AS other",
            "DETACH DATABASE other",
            "VACUUM",
            "CREATE TRIGGER audit AFTER INSERT ON users BEGIN SELECT 1; END",
            "CREATE VIRTUAL TABLE docs USING fts5(body)",
            "CREATE TEMP TABLE temp_users(id INTEGER)"
        )
        forbidden.forEach { sql ->
            assertFalse("Should reject: " + sql, runCatching {
                ScriptSqlPolicy.validate(sql, emptyList<Any?>(), ScriptSqlPolicy.Mode.EXECUTE)
            }.isSuccess)
        }
    }

    @Test
    fun rejectsMultipleStatementsCommentsAndNonPositionalParameters() {
        val invalid = listOf(
            "SELECT 1; DELETE FROM users",
            "SELECT * FROM users -- hidden filter",
            "SELECT * FROM users /* comment */",
            "SELECT * FROM users WHERE id = :id",
            "SELECT * FROM users WHERE id = @id",
            "SELECT * FROM users WHERE id = \$id",
            "SELECT * FROM users WHERE id = ?1"
        )
        invalid.forEach { sql ->
            assertFalse("Should reject: " + sql, runCatching {
                ScriptSqlPolicy.validate(sql, emptyList<Any?>(), ScriptSqlPolicy.Mode.QUERY)
            }.isSuccess)
        }
    }

    @Test
    fun requiresExactParameterCountAndScalarValues() {
        assertFalse(runCatching {
            ScriptSqlPolicy.validate("SELECT * FROM users WHERE id = ?", emptyList<Any?>(), ScriptSqlPolicy.Mode.QUERY)
        }.isSuccess)
        assertFalse(runCatching {
            ScriptSqlPolicy.validate("SELECT * FROM users", listOf(1), ScriptSqlPolicy.Mode.QUERY)
        }.isSuccess)
        assertFalse(runCatching {
            ScriptSqlPolicy.validate("SELECT * FROM users WHERE id = ?", listOf(listOf(1)), ScriptSqlPolicy.Mode.QUERY)
        }.isSuccess)
        assertFalse(runCatching {
            ScriptSqlPolicy.validate("SELECT * FROM users WHERE id = ?", listOf(Double.NaN), ScriptSqlPolicy.Mode.QUERY)
        }.isSuccess)
    }

    @Test
    fun blocksDirectReadAndMutationOfTheInternalMigrationHistory() {
        val forbidden = listOf(
            "SELECT * FROM _AUTU_SCHEMA_MIGRATIONS",
            "DROP TABLE _autu_schema_migrations",
            "INSERT INTO \"_AUTU_SCHEMA_MIGRATIONS\"(version) VALUES (1)"
        )
        forbidden.forEach { sql ->
            assertFalse("Should reject: " + sql, runCatching {
                val mode = if (sql.startsWith("SELECT")) {
                    ScriptSqlPolicy.Mode.QUERY
                } else {
                    ScriptSqlPolicy.Mode.EXECUTE
                }
                ScriptSqlPolicy.validate(sql, emptyList<Any?>(), mode)
            }.isSuccess)
        }
    }

    @Test
    fun enforcesSqlAndParameterLimits() {
        assertFalse(runCatching {
            ScriptSqlPolicy.validate(
                "SELECT " + " ".repeat(ScriptSqlPolicy.MAX_SQL_LENGTH),
                emptyList<Any?>(),
                ScriptSqlPolicy.Mode.QUERY
            )
        }.isSuccess)
        val tooManyParameters = List(ScriptSqlPolicy.MAX_PARAMETERS + 1) { it }
        val sql = "SELECT * FROM users WHERE " +
            tooManyParameters.indices.joinToString(" AND ") { "id != ?" }
        assertFalse(runCatching {
            ScriptSqlPolicy.validate(sql, tooManyParameters, ScriptSqlPolicy.Mode.QUERY)
        }.isSuccess)
    }
}
