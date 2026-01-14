package com.example.cloudBalanceBackend.config;

import com.example.cloudBalanceBackend.repository.RevokedTokenRepository;
import com.example.cloudBalanceBackend.security.CustomUserDetailsService;
import com.example.cloudBalanceBackend.security.JwtAuthenticationFilter;
import com.example.cloudBalanceBackend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final RevokedTokenRepository revokedTokenRepository;
    private final CustomUserDetailsService userDetailsService;

    //Main Spring Security configuration
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(
                        jwtUtil,
                        revokedTokenRepository,
                        userDetailsService
                );

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/error",
                                "/auth/login",
                                "/auth/login-manual",
                                "/api/health",
                                "/actuator/**",
                                "/dashboard/cost-explorer",
                                "/dashboard/cost-explorer/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // JWT filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS Configuration (FIXED & CORRECT)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);

        // Frontend URL
        config.setAllowedOrigins(List.of("http://localhost:5173"));

        // HTTP methods browser is allowed to use
        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        // Headers browser is allowed to send
        config.setAllowedHeaders(
                List.of("Authorization", "Content-Type")
        );

        // Headers browser can read
        config.setExposedHeaders(
                List.of("Authorization")
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Password encoder
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Authentication Provider
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {

        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());

        return provider;
    }

    // Authentication Manager
    @Bean
    public AuthenticationManager authenticationManager() {
        return new ProviderManager(
                List.of(authenticationProvider())
        );
    }
}
