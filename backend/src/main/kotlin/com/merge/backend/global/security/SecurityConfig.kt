package com.merge.backend.global.security

import com.merge.backend.domain.user.exception.UserErrorCode
import com.merge.backend.global.dto.ApiResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val customAuthenticationFilter: CustomAuthenticationFilter,
    private val objectMapper: ObjectMapper,
) {

    //허용할 프론트 주소 (application.yml, 배포 때 환경변수로 덮어씀)
    @Value("\${custom.cors.allowed-origins}")
    private lateinit var allowedOrigins: List<String>

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        HttpMethod.POST,
                        "/auth/signup",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/logout",
                    )
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup")
                    .permitAll()
                    // H2 Console 테스트용
                    .requestMatchers("/h2-console/**")
                    .permitAll()
                    // PAT-01 테스트용 임시
                    .requestMatchers("/api/v1/workplaces/**")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                    )
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            .headers { headers ->
                headers.frameOptions { frame ->
                    frame.sameOrigin()
                }
            }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .addFilterBefore(
                customAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java,
            )
            // 토큰 없는 요청은 Spring Security가 기본처리 하는데 대신 ApiResponse 형식으로 통일
            .exceptionHandling { exceptionHandling ->
                exceptionHandling
                    .authenticationEntryPoint { _, response, _ ->
                        val errorCode = UserErrorCode.AUTHENTICATION_REQUIRED

                        response.status = errorCode.getHttpStatus().value()
                        response.contentType = "application/json; charset=UTF-8"

                        val body = objectMapper.writeValueAsString(
                            ApiResponse.error(
                                errorCode.getCode(),
                                errorCode.getMessage(),
                            ),
                        )

                        response.writer.write(body)
                    }
            }

        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
