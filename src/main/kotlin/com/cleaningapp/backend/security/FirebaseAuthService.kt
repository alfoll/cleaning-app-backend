package com.cleaningapp.backend.security

import com.google.firebase.auth.FirebaseToken

interface FirebaseAuthService {
    fun verifyToken(idToken: String): FirebaseToken
}