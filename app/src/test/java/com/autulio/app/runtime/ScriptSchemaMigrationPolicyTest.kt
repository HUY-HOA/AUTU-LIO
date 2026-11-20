package com.autulio.app.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptSchemaMigrationPolicyTest {
    @Test
    fun computesStableChecksumForTheSameSqlAndParameters() {
        val first = ScriptSchemaMigrationPolicy.validateStatements(
            listOf(
                ScriptSchemaMigrationStatement(
                    "INSERT INTO customers(name) VALUES (?)",
                    listOf("LIO")
                )
            )
        )
        val repeated = ScriptSchemaMigrationPolicy.validateStatements(
            listOf(
                ScriptSchemaMigrationStatement(
                    "INSERT INTO customers(name) VALUES (?)",
                    listOf("LIO")
                )
            )
        )

        assertEquals(first.checksum, repeated.checksum)
        assertEquals(64, first.checksum.length)
        assertTrue(first.checksum.matches(Regex("^[a-f0-9]{64}$")))
    }

    @Test
    fun checksumChangesWhenSqlOrBoundValuesChange() {
        val base = ScriptSchemaMigrationPolicy.validateStatements(
            listOf(ScriptSchemaMigrationStatement(
                "INSERT INTO customers(name) VALUES (?)",
                listOf("LIO")
            ))
        ).checksum
        val changedSql = ScriptSchemaMigrationPolicy.validateStatements(
            listOf(ScriptSchemaMigrationStatement(
                "INSERT INTO customers(display_name) VALUES (?)",
                listOf("LIO")
            ))
        ).checksum
        val changedValue = ScriptSchemaMigrationPolicy.validateStatements(
            listOf(ScriptSchemaMigrationStatement(
                "INSERT INTO customers(name) VALUES (?)",
                listOf("AUTU")
            ))
        ).checksum

        assertNotEquals(base, changedSql)
        assertNotEquals(base, changedValue)
    }

    @Test
    fun appliesOnlyTheNextUnappliedVersionAndMakesExactRepeatsIdempotent() {
        val checksum = ScriptSchemaMigrationPolicy.validateStatements(
            listOf(ScriptSchemaMigrationStatement("CREATE TABLE customers(id INTEGER PRIMARY KEY)"))
        ).checksum

        assertTrue(ScriptSchemaMigrationPolicy.shouldApply(1, 0, null, checksum))
        assertFalse(ScriptSchemaMigrationPolicy.shouldApply(1, 1, checksum, checksum))
        assertTrue(ScriptSchemaMigrationPolicy.shouldApply(2, 1, null, checksum))
    }

    @Test
    fun rejectsChangedChecksumForAnAlreadyAppliedVersion() {
        val failed = runCatching {
            ScriptSchemaMigrationPolicy.shouldApply(
                requestedVersion = 1,
                currentVersion = 2,
                previouslyAppliedChecksum = "a".repeat(64),
                requestedChecksum = "b".repeat(64)
            )
        }
        assertFalse(failed.isSuccess)
    }

    @Test
    fun rejectsSkippedOrOutOfRangeVersions() {
        assertFalse(runCatching {
            ScriptSchemaMigrationPolicy.shouldApply(2, 0, null, "a".repeat(64))
        }.isSuccess)
        assertFalse(runCatching {
            ScriptSchemaMigrationPolicy.shouldApply(0, 0, null, "a".repeat(64))
        }.isSuccess)
        assertFalse(runCatching {
            ScriptSchemaMigrationPolicy.shouldApply(
                ScriptSchemaMigrationPolicy.MAX_VERSION + 1,
                ScriptSchemaMigrationPolicy.MAX_VERSION,
                null,
                "a".repeat(64)
            )
        }.isSuccess)
    }

    @Test
    fun enforcesMigrationStatementAndAggregateLimits() {
        assertFalse(runCatching {
            ScriptSchemaMigrationPolicy.validateStatements(emptyList())
        }.isSuccess)
        assertFalse(runCatching {
            ScriptSchemaMigrationPolicy.validateStatements(
                List(ScriptSchemaMigrationPolicy.MAX_STATEMENTS + 1) {
                    ScriptSchemaMigrationStatement("DELETE FROM customers")
                }
            )
        }.isSuccess)

        val paddedSql = "CREATE TABLE item(id INTEGER)" +
            " ".repeat(ScriptSqlPolicy.MAX_SQL_LENGTH - "CREATE TABLE item(id INTEGER)".length)
        assertFalse(runCatching {
            ScriptSchemaMigrationPolicy.validateStatements(
                List(9) { ScriptSchemaMigrationStatement(paddedSql) }
            )
        }.isSuccess)
    }

    @Test
    fun enforcesAggregateParameterLimitAcrossStatements() {
        val placeholders = List(ScriptSqlPolicy.MAX_PARAMETERS) { "?" }.joinToString(",")
        val sql = "DELETE FROM customers WHERE id IN ($placeholders)"
        val tooMany = List(4) {
            ScriptSchemaMigrationStatement(sql, List(ScriptSqlPolicy.MAX_PARAMETERS) { it })
        } + ScriptSchemaMigrationStatement(
            "DELETE FROM customers WHERE id = ?",
            listOf(1)
        )

        assertFalse(runCatching {
            ScriptSchemaMigrationPolicy.validateStatements(tooMany)
        }.isSuccess)
    }
}
