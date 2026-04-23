package com.cleaningapp.backend.config

import com.cleaningapp.backend.security.FirebaseAuthFilter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val firebaseAuthFilter: FirebaseAuthFilter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            // дополнения для проверки
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            // конец дополнений
            .authorizeHttpRequests {
                it
                    .requestMatchers("/api/ping").permitAll()
                    // для аутентфициованных пользователей,
                    // так как регистрация в бд происходит только с валидным fb токеном
                    .requestMatchers("/api/auth/register").authenticated()
                    .anyRequest().authenticated()
            }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // дополнения для проверки
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, exception ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json"
                    response.writer.write(
                        """{"error":"UNAUTHORIZED","message":"${exception.message ?: "Authentication required"}"}"""
                    )
                }
                it.accessDeniedHandler { _, response, exception ->
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = "application/json"
                    response.writer.write(
                        """{"error":"FORBIDDEN","message":"${exception.message ?: "Access denied"}"}"""
                    )
                }
            }
            // конец дополнений для проверки
            .addFilterBefore(firebaseAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
}