package com.cleaningapp.backend.exception


class UserNotActiveException : RuntimeException("User with this uid/email/id not active")