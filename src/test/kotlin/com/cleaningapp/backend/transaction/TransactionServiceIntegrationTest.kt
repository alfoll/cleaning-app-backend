package com.cleaningapp.backend.transaction

import com.cleaningapp.backend.base.BaseIntegrationTest
import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.PrivilegeNotFoundException
import com.cleaningapp.backend.exception.TaskNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.util.UUID

class TransactionServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var transactionService: TransactionService

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var userHouseholdRepository: UserHouseholdRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `recordTaskCompletion should create positive transaction and increase member balance`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 10,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
            reward = 25,
        )

        transactionService.recordTaskCompletion(
            TaskCompletionTransactionCommand(
                householdId = household.id!!,
                memberId = member.id!!,
                taskId = task.id!!,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.balance).isEqualTo(35)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.TASK_COMPLETION)
        assertThat(transactions.first().amount).isEqualTo(25)
        assertThat(transactions.first().household.id).isEqualTo(household.id)
        assertThat(transactions.first().member.id).isEqualTo(member.id)
        assertThat(transactions.first().task?.id).isEqualTo(task.id)
        assertThat(transactions.first().privilege).isNull()
    }

    @Test
    fun `recordTaskCompletion should reject nonexistent task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    taskId = UUID.randomUUID(),
                )
            )
        }.isInstanceOf(TaskNotFoundException::class.java)
    }

    @Test
    fun `recordTaskCompletion should reject nonexistent member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = household.id!!,
                    memberId = UUID.randomUUID(),
                    taskId = task.id!!,
                )
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `recordTaskCompletion should reject inactive member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    taskId = task.id!!,
                )
            )
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `recordTaskCompletion should reject inactive household through member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = true,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    taskId = task.id!!,
                )
            )
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `recordTaskCompletion should reject member from another household`() {
        val user = createLocalUserForValidToken()

        val householdA = testDataFactory.createTestHousehold(createdBy = user)
        val memberA = testDataFactory.createTestMembership(
            user = user,
            household = householdA,
        )

        val otherUser = testDataFactory.createTestUser()
        val householdB = testDataFactory.createTestHousehold(createdBy = otherUser)
        val memberB = testDataFactory.createTestMembership(
            user = otherUser,
            household = householdB,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = householdA,
            createdBy = user,
            completedBy = memberA,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = householdA.id!!,
                    memberId = memberB.id!!,
                    taskId = task.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `recordTaskCompletion should reject task from another household`() {
        val user = createLocalUserForValidToken()

        val householdA = testDataFactory.createTestHousehold(createdBy = user)
        val memberA = testDataFactory.createTestMembership(
            user = user,
            household = householdA,
        )

        val householdB = testDataFactory.createTestHousehold(createdBy = user)
        val memberB = testDataFactory.createTestMembership(
            user = user,
            household = householdB,
        )

        val taskFromHouseholdB = testDataFactory.createTestCompletedTask(
            household = householdB,
            createdBy = user,
            completedBy = memberB,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = householdA.id!!,
                    memberId = memberA.id!!,
                    taskId = taskFromHouseholdB.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `recordTaskCompletion should reject not completed task`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        val task = testDataFactory.createTestFreeTask(
            household = household,
            createdBy = user,
            reward = 20,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    taskId = task.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `recordTaskCompletion should reject completedBy mismatch`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val currentMember = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )
        val otherMember = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = otherMember,
            reward = 20,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = household.id!!,
                    memberId = currentMember.id!!,
                    taskId = task.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `recordTaskCompletion should reject duplicate task transaction and not increase balance again`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 10,
        )

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
            reward = 25,
        )

        testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = member,
            task = task,
            amount = 25,
        )

        assertThatThrownBy {
            transactionService.recordTaskCompletion(
                TaskCompletionTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    taskId = task.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.balance).isEqualTo(10)
        assertThat(transactions).hasSize(1)
    }

    @Test
    fun `recordPrivilegePurchase should create negative transaction and decrease member balance`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 90,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        transactionService.recordPrivilegePurchase(
            PrivilegePurchaseTransactionCommand(
                householdId = household.id!!,
                memberId = member.id!!,
                privilegeId = privilege.id!!,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.balance).isEqualTo(40)

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.PRIVILEGE_BOUGHT)
        assertThat(transactions.first().amount).isEqualTo(-50)
        assertThat(transactions.first().household.id).isEqualTo(household.id)
        assertThat(transactions.first().member.id).isEqualTo(member.id)
        assertThat(transactions.first().task).isNull()
        assertThat(transactions.first().privilege?.id).isEqualTo(privilege.id)
    }

    @Test
    fun `recordPrivilegePurchase should reject nonexistent privilege`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    privilegeId = UUID.randomUUID(),
                )
            )
        }.isInstanceOf(PrivilegeNotFoundException::class.java)
    }

    @Test
    fun `recordPrivilegePurchase should reject nonexistent member`() {
        val user = createLocalUserForValidToken()
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

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = household.id!!,
                    memberId = UUID.randomUUID(),
                    privilegeId = privilege.id!!,
                )
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `recordPrivilegePurchase should reject inactive member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
            isUserActive = false,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    privilegeId = privilege.id!!,
                )
            )
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }

    @Test
    fun `recordPrivilegePurchase should reject inactive household through member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
            isUserActive = true,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    privilegeId = privilege.id!!,
                )
            )
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `recordPrivilegePurchase should reject member from another household`() {
        val user = createLocalUserForValidToken()

        val householdA = testDataFactory.createTestHousehold(createdBy = user)
        val memberA = testDataFactory.createTestMembership(
            user = user,
            household = householdA,
            balance = 100,
        )

        val otherUser = testDataFactory.createTestUser()
        val householdB = testDataFactory.createTestHousehold(createdBy = otherUser)
        val memberB = testDataFactory.createTestMembership(
            user = otherUser,
            household = householdB,
            balance = 100,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = householdA,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = memberA,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = householdA.id!!,
                    memberId = memberB.id!!,
                    privilegeId = privilege.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `recordPrivilegePurchase should reject privilege from another household`() {
        val user = createLocalUserForValidToken()

        val householdA = testDataFactory.createTestHousehold(createdBy = user)
        val memberA = testDataFactory.createTestMembership(
            user = user,
            household = householdA,
            balance = 100,
        )

        val householdB = testDataFactory.createTestHousehold(createdBy = user)
        val memberB = testDataFactory.createTestMembership(
            user = user,
            household = householdB,
            balance = 100,
        )

        val privilegeFromHouseholdB = testDataFactory.createTestPrivilege(
            household = householdB,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = memberB,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = householdA.id!!,
                    memberId = memberA.id!!,
                    privilegeId = privilegeFromHouseholdB.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `recordPrivilegePurchase should reject available privilege`() {
        val user = createLocalUserForValidToken()
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
            isAvailable = true,
            boughtBy = null,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    privilegeId = privilege.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `recordPrivilegePurchase should reject boughtBy mismatch`() {
        val user = createLocalUserForValidToken()
        val otherUser = testDataFactory.createTestUser()

        val household = testDataFactory.createTestHousehold(createdBy = user)
        val currentMember = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )
        val otherMember = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
            balance = 100,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = otherMember,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = household.id!!,
                    memberId = currentMember.id!!,
                    privilegeId = privilege.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `recordPrivilegePurchase should reject insufficient balance`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 20,
        )

        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    privilegeId = privilege.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.balance).isEqualTo(20)
        assertThat(transactions).isEmpty()
    }

    @Test
    fun `recordPrivilegePurchase should reject duplicate privilege transaction and not decrease balance again`() {
        val user = createLocalUserForValidToken()
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

        testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = member,
            privilege = privilege,
            amount = -50,
        )

        assertThatThrownBy {
            transactionService.recordPrivilegePurchase(
                PrivilegePurchaseTransactionCommand(
                    householdId = household.id!!,
                    memberId = member.id!!,
                    privilegeId = privilege.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.balance).isEqualTo(100)
        assertThat(transactions).hasSize(1)
    }

    @Test
    fun `resetBalance should do nothing when balance is zero`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 0,
        )

        transactionService.resetBalance(
            BalanceResetTransactionCommand(
                householdId = household.id!!,
                memberId = member.id!!,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.balance).isZero()
        assertThat(transactions).isEmpty()
    }

    @Test
    fun `resetBalance should create balance reset transaction and set balance to zero`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 70,
        )

        transactionService.resetBalance(
            BalanceResetTransactionCommand(
                householdId = household.id!!,
                memberId = member.id!!,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.balance).isZero()

        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.BALANCE_RESET)
        assertThat(transactions.first().amount).isEqualTo(-70)
        assertThat(transactions.first().task).isNull()
        assertThat(transactions.first().privilege).isNull()
    }

    @Test
    fun `resetBalance should allow inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 70,
            isUserActive = false,
        )

        transactionService.resetBalance(
            BalanceResetTransactionCommand(
                householdId = household.id!!,
                memberId = member.id!!,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.isUserActive).isFalse()
        assertThat(updatedMember.balance).isZero()
        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.BALANCE_RESET)
        assertThat(transactions.first().amount).isEqualTo(-70)
    }

    @Test
    fun `resetBalance should allow inactive household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 70,
            isUserActive = true,
        )

        transactionService.resetBalance(
            BalanceResetTransactionCommand(
                householdId = household.id!!,
                memberId = member.id!!,
            )
        )

        entityManager.flush()
        entityManager.clear()

        val updatedMember = userHouseholdRepository.findById(member.id!!).orElseThrow()
        val transactions =
            transactionRepository.findAllByHouseholdIdAndMemberIdOrderByCreatedAtDesc(
                household.id!!,
                member.id!!,
            )

        assertThat(updatedMember.balance).isZero()
        assertThat(updatedMember.household.isActive).isFalse()
        assertThat(transactions).hasSize(1)
        assertThat(transactions.first().type).isEqualTo(TransactionType.BALANCE_RESET)
        assertThat(transactions.first().amount).isEqualTo(-70)
    }

    @Test
    fun `resetBalance should reject member from another household`() {
        val user = createLocalUserForValidToken()

        val householdA = testDataFactory.createTestHousehold(createdBy = user)
        val householdB = testDataFactory.createTestHousehold(createdBy = user)
        val memberB = testDataFactory.createTestMembership(
            user = user,
            household = householdB,
            balance = 70,
        )

        assertThatThrownBy {
            transactionService.resetBalance(
                BalanceResetTransactionCommand(
                    householdId = householdA.id!!,
                    memberId = memberB.id!!,
                )
            )
        }.isInstanceOf(BusinessConflictException::class.java)
    }

    @Test
    fun `resetBalance should reject nonexistent member`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)

        assertThatThrownBy {
            transactionService.resetBalance(
                BalanceResetTransactionCommand(
                    householdId = household.id!!,
                    memberId = UUID.randomUUID(),
                )
            )
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getMyTransactions should return only current member transactions in household sorted by createdAt desc`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val otherUser = testDataFactory.createTestUser()
        val otherMember = testDataFactory.createTestMembership(
            user = otherUser,
            household = household,
            balance = 100,
        )

        val otherHousehold = testDataFactory.createTestHousehold(createdBy = user)
        val otherHouseholdMember = testDataFactory.createTestMembership(
            user = user,
            household = otherHousehold,
            balance = 100,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val task = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = user,
            completedBy = member,
            reward = 20,
        )
        val privilege = testDataFactory.createTestPrivilege(
            household = household,
            createdBy = user,
            cost = 50,
            isAvailable = false,
            boughtBy = member,
        )

        val olderTransaction = testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = member,
            task = task,
            amount = 20,
            createdAt = baseTime.minusDays(3),
        )
        val newerTransaction = testDataFactory.createTestPrivilegePurchaseTransaction(
            household = household,
            member = member,
            privilege = privilege,
            amount = -50,
            createdAt = baseTime.minusDays(1),
        )
        val newestTransaction = testDataFactory.createTestBalanceResetTransaction(
            household = household,
            member = member,
            amount = -30,
            createdAt = baseTime,
        )

        val otherTask = testDataFactory.createTestCompletedTask(
            household = household,
            createdBy = otherUser,
            completedBy = otherMember,
            reward = 15,
        )
        val otherMemberTransaction = testDataFactory.createTestTaskCompletionTransaction(
            household = household,
            member = otherMember,
            task = otherTask,
            amount = 15,
            createdAt = baseTime.plusDays(1),
        )

        val otherHouseholdTask = testDataFactory.createTestCompletedTask(
            household = otherHousehold,
            createdBy = user,
            completedBy = otherHouseholdMember,
            reward = 10,
        )
        val otherHouseholdTransaction = testDataFactory.createTestTaskCompletionTransaction(
            household = otherHousehold,
            member = otherHouseholdMember,
            task = otherHouseholdTask,
            amount = 10,
            createdAt = baseTime.plusDays(2),
        )

        authenticateAs()

        val result = transactionService.getMyTransactions(household.id!!)

        assertThat(result.map { it.id })
            .containsExactly(
                newestTransaction.id,
                newerTransaction.id,
                olderTransaction.id,
            )

        assertThat(result.map { it.userId })
            .containsOnly(user.id)

        assertThat(result.map { it.householdId })
            .containsOnly(household.id)

        assertThat(result.map { it.id })
            .doesNotContain(
                otherMemberTransaction.id,
                otherHouseholdTransaction.id,
            )
    }

    @Test
    fun `getMyTransactions should return at most 150 newest transactions`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        val member = testDataFactory.createTestMembership(
            user = user,
            household = household,
            balance = 100,
        )

        val baseTime = LocalDateTime.parse("2026-04-27T12:00:00")

        val transactions = (1..151).map { index ->
            testDataFactory.createTestBalanceResetTransaction(
                household = household,
                member = member,
                amount = -index,
                createdAt = baseTime.plusMinutes(index.toLong()),
            )
        }

        authenticateAs()

        val result = transactionService.getMyTransactions(household.id!!)

        assertThat(result).hasSize(150)
        assertThat(result.first().id).isEqualTo(transactions.last().id)
        assertThat(result.last().id).isEqualTo(transactions[1].id)
        assertThat(result.map { it.id }).doesNotContain(transactions.first().id)
    }

    @Test
    fun `getMyTransactions should return empty list when current member has no transactions`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
        )

        authenticateAs()

        val result = transactionService.getMyTransactions(household.id!!)

        assertThat(result).isEmpty()
    }

    @Test
    fun `getMyTransactions should reject inactive user`() {
        createLocalUserForValidToken(isActive = false)

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)

        authenticateAs()

        assertThatThrownBy {
            transactionService.getMyTransactions(household.id!!)
        }.isInstanceOf(UserNotActiveException::class.java)
    }

    @Test
    fun `getMyTransactions should reject inactive household`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(
            createdBy = user,
            isActive = false,
        )
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = true,
        )

        authenticateAs()

        assertThatThrownBy {
            transactionService.getMyTransactions(household.id!!)
        }.isInstanceOf(HouseholdNotActiveException::class.java)
    }

    @Test
    fun `getMyTransactions should reject nonexistent household`() {
        createLocalUserForValidToken()

        authenticateAs()

        assertThatThrownBy {
            transactionService.getMyTransactions(UUID.randomUUID())
        }.isInstanceOf(HouseholdNotFoundException::class.java)
    }

    @Test
    fun `getMyTransactions should reject non member`() {
        createLocalUserForValidToken()

        val owner = testDataFactory.createTestUser()
        val household = testDataFactory.createTestHousehold(createdBy = owner)
        testDataFactory.createTestMembership(
            user = owner,
            household = household,
        )

        authenticateAs()

        assertThatThrownBy {
            transactionService.getMyTransactions(household.id!!)
        }.isInstanceOf(MembershipNotFoundException::class.java)
    }

    @Test
    fun `getMyTransactions should reject inactive membership`() {
        val user = createLocalUserForValidToken()
        val household = testDataFactory.createTestHousehold(createdBy = user)
        testDataFactory.createTestMembership(
            user = user,
            household = household,
            isUserActive = false,
        )

        authenticateAs()

        assertThatThrownBy {
            transactionService.getMyTransactions(household.id!!)
        }.isInstanceOf(MembershipNotActiveException::class.java)
    }
}
