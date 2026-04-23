package com.cleaningapp.backend.security

import com.cleaningapp.backend.user.UserRepository
import com.cleaningapp.backend.user.toUserDetails
import com.google.firebase.auth.FirebaseAuthException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

typealias SpringUser = org.springframework.security.core.userdetails.User


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
        // если это ping то полностью пропускаем
        if (request.requestURI.startsWith("/api/ping")) {
            filterChain.doFilter(request, response)
            return
        }

        val authHeader: String? = request.getHeader("Authorization")

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            SecurityContextHolder.clearContext()
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error": "Firebase token required"}""")
            return
        }

        val token = authHeader.substringAfter("Bearer ")

        try {
            // верифицируем и достаем токен и уид
            val firebaseToken = firebaseAuthService.verifyToken(token)
            val firebaseUid = firebaseToken.uid

            // если это регистрация, создаём только объект UserDetails без проверки БД
            val user: UserDetails = if (request.requestURI.startsWith("/api/auth/register")) {
                SpringUser.builder()
                    .username(firebaseUid) // в имени хранится fb uid
                    .password(null)
                    .roles("USER")
                    .build()
            } else {
                // для всех остальных запросов проверяем наличие пользователя в БД
                val dbUser = userRepository.findUserByFirebaseUid(firebaseUid)
                    ?: run {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found")
                        return
                    }
                // проверка на активность юзера уже на этапе фильтра, далее в сервисах тоже есть
                if (!dbUser.isActive) {
                    SecurityContextHolder.clearContext()
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = "application/json"
                    response.writer.write("""{"error":"User account is deactivated"}""")
                    return
                }
                dbUser.toUserDetails()
            }

            // в principal поле кладем объект UserDetails -> его в контекст
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