package com.cleaningapp.backend.exception

class UserNotFoundException : RuntimeException("User with this uid/email/id not found")