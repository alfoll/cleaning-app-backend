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
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "`transaction`")
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
    @JoinColumn(name = "task_id", updatable = false) // подумать про уникальность
    var task: TaskEntity? = null

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "privilege_id", updatable = false) // подумать про уникальность
    var privilege: PrivilegeEntity? = null
}