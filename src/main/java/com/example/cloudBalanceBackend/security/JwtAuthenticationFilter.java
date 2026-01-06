package com.example.cloudBalanceBackend.security;

import com.example.cloudBalanceBackend.model.RevokedToken;
import com.example.cloudBalanceBackend.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * JWT Authentication Filter
 * Validates JWT tokens and loads user details from database
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RevokedTokenRepository revokedTokenRepo;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Extract token from Authorization header
        String header = request.getHeader("Authorization");

        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            log.trace("No Bearer token found in request");
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            // Check if token is revoked (logged out)
            Optional<RevokedToken> revoked = revokedTokenRepo.findByToken(token);
            if (revoked.isPresent()) {
                log.warn("Attempted access with revoked token");
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                return;
            }

            // Validate token and extract claims
            var jws = jwtUtil.validate(token);
            Claims claims = jws.getBody();
            String userId = claims.getSubject();

            log.debug("Processing valid JWT for user ID: {}", userId);

            // Check if user is already authenticated
            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                // Load user details from database
                UserDetails userDetails;
                try {
                    userDetails = userDetailsService.loadUserById(userId);
                } catch (UsernameNotFoundException e) {
                    log.error("User not found in database for ID: {}", userId);
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "User not found");
                    return;
                }

                // Create authentication token with user details
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // Set additional details (IP address, session ID, etc.)
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Set authentication in security context
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Successfully authenticated user: {} with role: {}",
                        userDetails.getUsername(),
                        userDetails.getAuthorities()
                );
            }

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token expired");
            return;

        } catch (io.jsonwebtoken.MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token format");
            return;

        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid token signature");
            return;

        } catch (Exception ex) {
            log.error("JWT authentication error: {}", ex.getMessage(), ex);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication failed");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Helper method to send JSON error responses
     */
    private void sendErrorResponse(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(String.format("{\"error\":\"%s\"}", message));
    }
}