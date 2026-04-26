package com.cleaningapp.backend.exception

import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    // пользователь уже существует
    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT) // 409
    fun handleUserAlreadyExistsException(e: UserAlreadyExistsException): ExcResponse {
        return ExcResponse("409 USER_ALREADY_EXISTS", e.message)
    }

    // не найден пользователь
    @ExceptionHandler(UserNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND) // 404
    fun handleUserNotFoundException(e: UserNotFoundException): ExcResponse {
        return ExcResponse("404 USER_NOT_FOUND", e.message)
    }

    // пользователь не активен
    @ExceptionHandler(UserNotActiveException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN) // 403
    fun handleUserNotActiveException(e: UserNotActiveException): ExcResponse {
        return ExcResponse("403 USER_NOT_ACTIVE", e.message)
    }

    // почта занята
    @ExceptionHandler(EmailAlreadyUsedException::class)
    @ResponseStatus(HttpStatus.CONFLICT) //409
    fun handleEmailAlreadyUserException(e: EmailAlreadyUsedException): ExcResponse {
        return ExcResponse("409 EMAIL_ALREADY_USED", e.message)
    }

    // хозяйство не найдено
    @ExceptionHandler(HouseholdNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND) // 404
    fun handleHouseholdNotFoundException(e: HouseholdNotFoundException): ExcResponse {
        return ExcResponse("404 HOUSEHOLD_NOT_FOUND", e.message)
    }

    // хозяйство не активно
    @ExceptionHandler(HouseholdNotActiveException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN) // 403
    fun handleHouseholdNotActiveException(e: HouseholdNotActiveException): ExcResponse {
        return ExcResponse("403 HOUSEHOLD_NOT_ACTIVE", e.message)
    }

    // связь юзер <-> хозяйство не найдена (юзер не состоит в хозяйстве)
    @ExceptionHandler(MembershipNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND) // 404
    fun handleMembershipNotFoundException(e: MembershipNotFoundException): ExcResponse {
        return ExcResponse("404 MEMBERSHIP_NOT_FOUND", e.message)
    }

    // связь юзер <-> хозяйство не активна (юзер не активен в хозяйстве)
    @ExceptionHandler(MembershipNotActiveException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN) // 403
    fun handleMembershipNotActiveException(e: MembershipNotActiveException): ExcResponse {
        return ExcResponse("403 MEMBERSHIP_NOT_ACTIVE", e.message)
    }

    // задача не найдена
    @ExceptionHandler(TaskNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND) // 404
    fun handleTaskNotFoundException(e: TaskNotFoundException): ExcResponse {
        return ExcResponse("404 TASK_NOT_FOUND", e.message)
    }

    // привилегия не найдена
    @ExceptionHandler(PrivilegeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND) // 404
    fun handlePrivilegeNotFoundException(e: PrivilegeNotFoundException): ExcResponse {
        return ExcResponse("404 PRIVILEGE_NOT_FOUND", e.message)
    }

    // конфликты бизнес логики
    @ExceptionHandler(BusinessConflictException::class)
    @ResponseStatus(HttpStatus.CONFLICT) // 409
    fun handleBusinessConflictException(e: BusinessConflictException): ExcResponse {
        return ExcResponse("409 BUSINESS_CONFLICT", e.message?: "Business conflict")
    }

    // пользователь не авторизован, не получается достать
    @ExceptionHandler(AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN) // 403
    fun handleAccessDeniedException(e: AccessDeniedException): ExcResponse {
        return ExcResponse("403 ACCESS_DENIED", e.message)
    }

    // ошибки бд/Hibernate (нарушение ограничений бд)
    @ExceptionHandler(DataIntegrityViolationException::class)
    @ResponseStatus(HttpStatus.CONFLICT) // 409
    fun handleDataIntegrityViolationException(e: DataIntegrityViolationException): ExcResponse {
        return ExcResponse("409 DATA_INTEGRITY_VIOLATION", e.message)
    }

    // ошибки чтения/доступа к бд
    @ExceptionHandler(DataAccessException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleDataAccessException(e: DataAccessException): ExcResponse {
        e.printStackTrace()
        return ExcResponse("500 DATABASE_ERROR", "Database operation failed")
    }

    // валидация дто
    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // 400
    fun handleMethodArgumentNotValidException(e: MethodArgumentNotValidException): ExcResponse {
        val message = e.bindingResult.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ExcResponse("400 VALIDATION_ERROR", message)
    }

    // неправильные query/path параметры
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // 400
    fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ExcResponse {
        val message = when {
            e.requiredType?.isEnum == true ->
                "Invalid value '${e.value}' for parameter '${e.name}'"

            else ->
                "Invalid value '${e.value}' for parameter '${e.name}'"
        }
        return ExcResponse("400 INVALID_PARAMETER", message)
    }

    // битый JSON / неверный enum в body
    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // 400
    fun handleHttpMessageNotReadableException(e: HttpMessageNotReadableException): ExcResponse {
        return ExcResponse("400 MALFORMED_REQUEST", "Request body is invalid or malformed")
    }

    // некоррекнтые аргументы от пользователя
    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // 400
    fun handleIllegalArgumentException(e: IllegalArgumentException): ExcResponse {
        return ExcResponse("400 ILLEGAL_ARGUMENT", e.message)
    }

    // остальной рандом (IllegalStateException сюда)
    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // 500
    fun handleException(e: Exception): ExcResponse {
        e.printStackTrace() // стектрейс
        return ExcResponse("500 INTERNAL_SERVER_ERROR", "Unexpected server error")
    }
}