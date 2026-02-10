package com.cleaningapp.backend.security

import com.cleaningapp.backend.user.UserRegisterDTO
import com.cleaningapp.backend.user.UserResponseDTO
import com.cleaningapp.backend.user.UserService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping(value = ["/api/auth"])
class AuthController(
    private val userService: UserService
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody user: UserRegisterDTO,
                 @AuthenticationPrincipal principal: Principal): UserResponseDTO =
        userService.createUser(principal.name, user)

}