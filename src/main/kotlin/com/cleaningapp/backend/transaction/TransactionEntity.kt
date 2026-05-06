package com.cleaningapp.backend.transaction

import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.privilege.PrivilegeEntity
import com.cleaningapp.backend.task.TaskEntity
import com.cleaningapp.backend.userhousehold.UserHouseholdEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "transaction",
    indexes = [
        // транзакции хозяйства (индекс для чтения)
        Index(
            name = "idx_transaction_household_id_created_at",
            columnList = "household_id, created_at"
        ),
        // транзакции участника - мб ваще не надо (индекс для чтения)
        Index(
            name = "idx_transaction_member_id_created_at",
            columnList = "member_id, created_at"
        ),
        // лидерборд
        Index(
            name = "idx_transaction_leaderboard",
            columnList = "household_id, type, member_id, created_at"
        )
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_transaction_task_id",
            columnNames = ["task_id"]
        ),
        UniqueConstraint(
            name = "uk_transaction_privilege_id",
            columnNames = ["privilege_id"]
        )
    ]
)
class TransactionEntity(

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", nullable = false, updatable = false)
    var id: UUID? = null, // JPA сам генерит id при сохранении в бд

    @Column(name = "amount", nullable = false, updatable = false)
    var amount: Int,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 32)
    var type: TransactionType,

) {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id", nullable = false, updatable = false)
    lateinit var household: HouseholdEntity

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    lateinit var member: UserHouseholdEntity

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", updatable = false, unique = true) // подумать про уникальность
    var task: TaskEntity? = null

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "privilege_id", updatable = false, unique = true) // подумать про уникальность
    var privilege: PrivilegeEntity? = null
}