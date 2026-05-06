package com.cleaningapp.backend.base

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.LocalDateTime
import java.util.UUID

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

    @Test
    fun `database should reject negative membership balance`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)

        assertConstraintViolation("ck_user_household_balance_non_negative") {
            jdbcTemplate.update(
                """
                insert into user_household (
                    id, user_id, household_id, balance, joined_at, is_user_active, version
                ) values (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                user.id!!,
                household.id!!,
                -1,
                LocalDateTime.now(),
                true,
                0L,
            )
        }
    }

    @Test
    fun `database should reject task reward outside allowed range`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)

        assertConstraintViolation("ck_task_reward_range") {
            jdbcTemplate.update(
                """
                insert into task (
                    id, household_id, created_by, created_at, title, description,
                    reward, assigned_to, assigned_at, is_completed, completed_by, completed_at, version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                household.id!!,
                user.id!!,
                LocalDateTime.now(),
                "Invalid task",
                "Invalid reward",
                4,
                null,
                null,
                false,
                null,
                null,
                0L,
            )
        }
    }

    @Test
    fun `database should reject task assignment inconsistency`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(user = user, household = household)

        assertConstraintViolation("ck_task_state_consistency") {
            jdbcTemplate.update(
                """
                insert into task (
                    id, household_id, created_by, created_at, title, description,
                    reward, assigned_to, assigned_at, is_completed, completed_by, completed_at, version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                household.id!!,
                user.id!!,
                LocalDateTime.now(),
                "Invalid task",
                "Missing assignedAt",
                20,
                member.id!!,
                null,
                false,
                null,
                null,
                0L,
            )
        }
    }

    @Test
    fun `database should reject task completion inconsistency`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)

        assertConstraintViolation("ck_task_state_consistency") {
            jdbcTemplate.update(
                """
                insert into task (
                    id, household_id, created_by, created_at, title, description,
                    reward, assigned_to, assigned_at, is_completed, completed_by, completed_at, version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                household.id!!,
                user.id!!,
                LocalDateTime.now(),
                "Invalid task",
                "Completed without performer",
                20,
                null,
                null,
                true,
                null,
                null,
                0L,
            )
        }
    }

    @Test
    fun `database should reject privilege cost outside allowed range`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)

        assertConstraintViolation("ck_privilege_cost_range") {
            jdbcTemplate.update(
                """
                insert into privilege (
                    id, household_id, created_by, created_at, title, description,
                    cost, is_available, bought_by, version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                household.id!!,
                user.id!!,
                LocalDateTime.now(),
                "Invalid privilege",
                "Invalid cost",
                0,
                true,
                null,
                0L,
            )
        }
    }

    @Test
    fun `database should reject privilege availability inconsistency`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)

        assertConstraintViolation("ck_privilege_availability_consistency") {
            jdbcTemplate.update(
                """
                insert into privilege (
                    id, household_id, created_by, created_at, title, description,
                    cost, is_available, bought_by, version
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                household.id!!,
                user.id!!,
                LocalDateTime.now(),
                "Invalid privilege",
                "Unavailable without buyer",
                50,
                false,
                null,
                0L,
            )
        }
    }

    @Test
    fun `database should reject transaction payload inconsistent with type`() {
        val user = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )
        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        assertConstraintViolation("ck_transaction_payload_by_type") {
            jdbcTemplate.update(
                """
                insert into "transaction" (
                    id, household_id, member_id, amount, created_at, type, task_id, privilege_id
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                household.id!!,
                member.id!!,
                10,
                LocalDateTime.now(),
                "TASK_COMPLETION",
                null,
                privilege.id!!,
            )
        }
    }

    private fun assertConstraintViolation(
        constraintName: String,
        block: () -> Unit,
    ) {
        assertThatThrownBy(block)
            .isInstanceOf(DataIntegrityViolationException::class.java)
            .hasMessageContaining(constraintName)
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
