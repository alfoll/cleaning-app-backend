package com.cleaningapp.backend.tasktemplate

import com.cleaningapp.backend.exception.BusinessConflictException
import com.cleaningapp.backend.exception.HouseholdNotActiveException
import com.cleaningapp.backend.exception.HouseholdNotFoundException
import com.cleaningapp.backend.exception.MembershipNotActiveException
import com.cleaningapp.backend.exception.MembershipNotFoundException
import com.cleaningapp.backend.exception.TaskTemplateNotFoundException
import com.cleaningapp.backend.exception.UserNotActiveException
import com.cleaningapp.backend.exception.UserNotFoundException
import com.cleaningapp.backend.household.HouseholdEntity
import com.cleaningapp.backend.household.HouseholdRepository
import com.cleaningapp.backend.user.UserEntity
import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.userhousehold.UserHouseholdRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class TaskTemplateServiceImpl(
    private val taskTemplateRepository: TaskTemplateRepository,
    private val userRepository: UserRepository,
    private val householdRepository: HouseholdRepository,
    private val userHouseholdRepository: UserHouseholdRepository,
) : TaskTemplateService {

    private fun getCurrentUser(): UserEntity {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("User not authenticated")

        val user = userRepository.findUserByFirebaseUid(auth.name)
            ?: throw UserNotFoundException()

        if (!user.isActive)
            throw UserNotActiveException()

        return user
    }

    private fun getActiveHousehold(householdId: UUID): HouseholdEntity {
        val household = householdRepository.findByIdOrNull(householdId)
            ?: throw HouseholdNotFoundException()

        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }

    private fun getActiveHouseholdForUpdate(householdId: UUID): HouseholdEntity {
        val household = householdRepository.findByIdForUpdate(householdId)
            ?: throw HouseholdNotFoundException()

        if (!household.isActive)
            throw HouseholdNotActiveException()

        return household
    }

    private fun validateActiveMembership(userId: UUID, householdId: UUID) {
        val membership = userHouseholdRepository.findByUserIdAndHouseholdId(userId, householdId)
            ?: throw MembershipNotFoundException()

        if (!membership.isUserActive)
            throw MembershipNotActiveException()
    }

    private fun validateActiveMembershipForUpdate(userId: UUID, householdId: UUID) {
        val membership = userHouseholdRepository.findByUserIdAndHouseholdIdForUpdate(userId, householdId)
            ?: throw MembershipNotFoundException()

        if (!membership.isUserActive)
            throw MembershipNotActiveException()
    }

    private fun getTemplateForUpdate(templateId: UUID): TaskTemplateEntity =
        taskTemplateRepository.findByIdForUpdate(templateId)
            ?: throw TaskTemplateNotFoundException()

    @Transactional(readOnly = true)
    override fun getHouseholdTemplates(householdId: UUID): List<TaskTemplateResponseDTO> {
        val user = getCurrentUser()
        val household = getActiveHousehold(householdId)
        validateActiveMembership(user.id!!, household.id!!)

        return taskTemplateRepository
            .findAllByHouseholdIdAndIsActiveTrueOrderByCreatedAtDesc(household.id!!)
            .map { it.toDto() }
    }

    override fun createTemplate(
        householdId: UUID,
        template: TaskTemplateRegisterDTO,
    ): TaskTemplateResponseDTO {
        val user = getCurrentUser()
        val household = getActiveHouseholdForUpdate(householdId)
        validateActiveMembershipForUpdate(user.id!!, household.id!!)

        return taskTemplateRepository
            .save(template.toEntity(household, user))
            .toDto()
    }

    override fun updateTemplate(
        templateId: UUID,
        template: TaskTemplateRegisterDTO,
    ): TaskTemplateResponseDTO {
        val user = getCurrentUser()
        val householdId = taskTemplateRepository.findHouseholdIdByTemplateId(templateId)
            ?: throw TaskTemplateNotFoundException()

        val household = getActiveHouseholdForUpdate(householdId)
        validateActiveMembershipForUpdate(user.id!!, household.id!!)
        val entity = getTemplateForUpdate(templateId)

        if (entity.household.id != household.id)
            throw BusinessConflictException("Task template does not belong to this household")

        if (!entity.isActive)
            throw BusinessConflictException("Inactive task template cannot be updated")

        if (entity.createdBy.id != user.id)
            throw BusinessConflictException("Only creator can update task template")

        entity.title = template.title
        entity.description = template.description
        entity.reward = template.reward

        return entity.toDto()
    }

    override fun deleteTemplate(templateId: UUID) {
        val user = getCurrentUser()
        val householdId = taskTemplateRepository.findHouseholdIdByTemplateId(templateId)
            ?: throw TaskTemplateNotFoundException()

        val household = getActiveHouseholdForUpdate(householdId)
        validateActiveMembershipForUpdate(user.id!!, household.id!!)
        val entity = getTemplateForUpdate(templateId)

        if (entity.household.id != household.id)
            throw BusinessConflictException("Task template does not belong to this household")

        if (!entity.isActive)
            throw BusinessConflictException("Inactive task template cannot be deleted")

        if (entity.createdBy.id != user.id)
            throw BusinessConflictException("Only creator can delete task template")

        entity.isActive = false
    }
}
