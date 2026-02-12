package com.cleaningapp.backend.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    // пользователь уже существует
    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT) // 409
    fun handleUserAlreadyExistsException(e: UserAlreadyExistsException): ExcResponse {
        return ExcResponse("409 USER_ALREADY_EXISTS", "User with this uid already exists")
    }

    // не найден пользователь
    @ExceptionHandler(UserNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND) // 404
    fun handleUserNotFoundException(e: UserNotFoundException): ExcResponse {
        return ExcResponse("404 NOT_FOUND", "User with this uid/email not found")
    }

    // почта занята
    @ExceptionHandler(EmailAlreadyUserException::class)
    @ResponseStatus(HttpStatus.CONFLICT) //409
    fun handleEmailAlreadyUserException(e: EmailAlreadyUserException): ExcResponse {
        return ExcResponse("409 EMAIL_ALREADY_USED", "Email is already used")
    }


    // остальной рандом
    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR) // 500
    fun handleException(e: Exception): ExcResponse {
        e.printStackTrace() // стектрейс
        return ExcResponse("500 INTERNAL_SERVER_ERROR", e.message)
    }
}