package com.cleaningapp.backend.security

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import org.springframework.stereotype.Service

@Service
class FirebaseAuthServiceImpl(
    private val firebaseAuth: FirebaseAuth
) : FirebaseAuthService {
    override fun verifyToken(idToken: String): FirebaseToken {
        return firebaseAuth.verifyIdToken(idToken)
    }
}