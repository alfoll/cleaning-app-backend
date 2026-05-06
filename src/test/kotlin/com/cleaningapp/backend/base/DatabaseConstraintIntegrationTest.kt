package com.cleaningapp.backend.base

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DatabaseConstraintIntegrationTest : BaseConcurrencyIntegrationTest() {

    @Test
    fun `database should contain required unique constraints`() {
        val uniqueConstraints = loadUniqueConstraints()

        assertUniqueConstraintExists(
            uniqueConstraints = uniqueConstraints,
            tableName = "user",
            columns = listOf("firebase_uid"),
        )

        assertUniqueConstraintExists(
            uniqueConstraints = uniqueConstraints,
            tableName = "user",
            columns = listOf("email"),
        )

        assertUniqueConstraintExists(
            uniqueConstraints = uniqueConstraints,
            tableName = "household",
            columns = listOf("invite_code"),
        )

        assertUniqueConstraintExists(
            uniqueConstraints = uniqueConstraints,
            tableName = "user_household",
            columns = listOf("user_id", "household_id"),
        )

        assertUniqueConstraintExists(
            uniqueConstraints = uniqueConstraints,
            tableName = "transaction",
            columns = listOf("task_id"),
        )

        assertUniqueConstraintExists(
            uniqueConstraints = uniqueConstraints,
            tableName = "transaction",
            columns = listOf("privilege_id"),
        )
    }

    private fun loadUniqueConstraints(): List<UniqueConstraintInfo> {
        val rows = jdbcTemplate.query(
            """
            select
                tc.table_name,
                tc.constraint_name,
                kcu.column_name,
                kcu.ordinal_position
            from information_schema.table_constraints tc
            join information_schema.key_column_usage kcu
              on tc.constraint_name = kcu.constraint_name
             and tc.table_schema = kcu.table_schema
             and tc.table_name = kcu.table_name
            where tc.table_schema = 'public'
              and tc.constraint_type = 'UNIQUE'
              and tc.table_name in (
                  'user',
                  'household',
                  'user_household',
                  'transaction'
              )
            order by
                tc.table_name,
                tc.constraint_name,
                kcu.ordinal_position
            """.trimIndent()
        ) { rs, _ ->
            UniqueConstraintColumnRow(
                tableName = rs.getString("table_name"),
                constraintName = rs.getString("constraint_name"),
                columnName = rs.getString("column_name"),
                ordinalPosition = rs.getInt("ordinal_position"),
            )
        }

        return rows
            .groupBy { it.tableName to it.constraintName }
            .map { (key, groupedRows) ->
                UniqueConstraintInfo(
                    tableName = key.first,
                    constraintName = key.second,
                    columns = groupedRows
                        .sortedBy { it.ordinalPosition }
                        .map { it.columnName },
                )
            }
    }

    private fun assertUniqueConstraintExists(
        uniqueConstraints: List<UniqueConstraintInfo>,
        tableName: String,
        columns: List<String>,
    ) {
        assertThat(uniqueConstraints)
            .withFailMessage {
                """
                Expected unique constraint on $tableName(${columns.joinToString(", ")}).
                
                Actual unique constraints:
                ${uniqueConstraints.joinToString(separator = "\n") {
                    "${it.tableName}.${it.constraintName}(${it.columns.joinToString(", ")})"
                }}
                """.trimIndent()
            }
            .anySatisfy { constraint ->
                assertThat(constraint.tableName).isEqualTo(tableName)
                assertThat(constraint.columns).isEqualTo(columns)
            }
    }

    private data class UniqueConstraintColumnRow(
        val tableName: String,
        val constraintName: String,
        val columnName: String,
        val ordinalPosition: Int,
    )

    private data class UniqueConstraintInfo(
        val tableName: String,
        val constraintName: String,
        val columns: List<String>,
    )
}