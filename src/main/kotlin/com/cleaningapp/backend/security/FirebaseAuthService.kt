package com.cleaningapp.backend.security

import com.google.firebase.auth.FirebaseToken
import com.google.firebase.auth.UserRecord

interface FirebaseAuthService {
    // проверка токена
    fun verifyToken(idToken: String): FirebaseToken

    // достать юзера по uid
    fun detUserByUid(uid: String): UserRecord
}