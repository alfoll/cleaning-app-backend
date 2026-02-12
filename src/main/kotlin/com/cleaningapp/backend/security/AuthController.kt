package com.cleaningapp.backend.security

import com.cleaningapp.backend.user.UserRegisterDTO
import com.cleaningapp.backend.user.UserResponseDTO
import com.cleaningapp.backend.user.UserService
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = ["/api/auth"])
class AuthController(
    private val userService: UserService
) {
    @PostMapping("/register")
    fun register(@Valid @RequestBody user: UserRegisterDTO,
        // fb uid берется из токена и помещается в UserDetails (ранее было Principal),
        // тут берется из созданного в фильтре объекта UserDetails
                 @AuthenticationPrincipal userDetails: UserDetails?): UserResponseDTO {
        if (userDetails == null) {
            throw RuntimeException("Principal is null")
        }
            return userService.createUser(userDetails.username, user)
    }
}
// было Principal стало UserDetails