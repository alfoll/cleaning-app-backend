package com.cleaningapp.backend.security

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.google.firebase.auth.UserRecord
import org.springframework.stereotype.Service

@Service
class FirebaseAuthServiceImpl(
    private val firebaseAuth: FirebaseAuth
) : FirebaseAuthService {

    override fun verifyToken(idToken: String): FirebaseToken {
        return firebaseAuth.verifyIdToken(idToken)
    }

    override fun detUserByUid(uid: String): UserRecord =
        firebaseAuth.getUser(uid)
}