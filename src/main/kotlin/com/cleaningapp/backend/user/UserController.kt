package com.cleaningapp.backend.user

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {
    @GetMapping("/me")
    fun getProfile(@AuthenticationPrincipal principal: Principal): UserResponseDTO =
        userService.findUserByFirebaseUid(principal.name)

    @PutMapping("/me")
    fun updateProfile(@AuthenticationPrincipal principal: Principal,
                      @Valid @RequestBody newUser: UserRegisterDTO): UserResponseDTO =
        userService.updateProfile(principal.name, newUser)

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProfile(@AuthenticationPrincipal principal: Principal) =
        userService.deleteUser(principal.name)
}