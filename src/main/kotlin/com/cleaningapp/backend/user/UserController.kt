package com.cleaningapp.backend.user

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
class UserController(
    private val userService: UserService
) {
    @GetMapping("/me")
    // firebase uid достается из контекста из UserDetails (username)
    // (idToken от fb проверяется на валидность в фильтре на каждый эндпойнт -> uid в UserDetails как username)
    fun getProfile(@AuthenticationPrincipal userDetails: UserDetails): UserResponseDTO =
        userService.findUserByFirebaseUid(userDetails.username)

    // обновление профиля не включает смену почты
    @PutMapping("/me")
    fun updateProfile(@AuthenticationPrincipal userDetails: UserDetails,
                      @Valid @RequestBody newUser: UserUpdateDTO): UserResponseDTO =
        userService.updateProfile(userDetails.username, newUser)

    // обновление почты - отдельный сценарий
    // запрос на синхронизацию почты в локальной бд после смены ее на Firebase Auth портале
    @PutMapping("/me/email/sync")
    fun syncEmailFromFirebase(@AuthenticationPrincipal userDetails: UserDetails): UserResponseDTO =
        userService.syncEmailFromFirebase(userDetails.username)

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteProfile(@AuthenticationPrincipal userDetails: UserDetails) =
        userService.deleteUser(userDetails.username)

    // хозяйства пользователя можно взять через UserHouseholdController
}