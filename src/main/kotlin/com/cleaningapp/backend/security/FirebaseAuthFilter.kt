package com.cleaningapp.backend.security

import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.user.toUserDetails
import com.google.firebase.auth.FirebaseAuthException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class FirebaseAuthFilter (
    private val firebaseAuthService: FirebaseAuthService,
    private val userRepository: UserRepository,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader: String? = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substringAfter("Bearer ")

        try {

            val firebaseToken = firebaseAuthService.verifyToken(token)
            val firebaseUid = firebaseToken.uid

            val user = userRepository.findUserByFirebaseUid(firebaseUid)?.toUserDetails()
                ?: run {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found")
                    return
                }

            val authToken = UsernamePasswordAuthenticationToken(user, null, user.authorities)
            SecurityContextHolder.getContext().authentication = authToken

        } catch (ex: FirebaseAuthException) {
            SecurityContextHolder.clearContext()
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error": "Invalid or expired Firebase token"}""")
            return
        } catch (ex: UsernameNotFoundException) {
            SecurityContextHolder.clearContext()
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error": "User with this uid does not found"}""")
            return
        } catch (ex: IllegalArgumentException) {
            SecurityContextHolder.clearContext()
            response.status = HttpServletResponse.SC_BAD_REQUEST
            response.contentType = "application/json"
            response.writer.write("""{"error": "Invalid token format"}""")
            return
        } catch (ex: Exception) {
            SecurityContextHolder.clearContext()
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected error")
            return
        }

        filterChain.doFilter(request, response)
    }
}