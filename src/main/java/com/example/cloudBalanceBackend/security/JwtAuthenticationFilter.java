package com.example.cloudBalanceBackend.security;

import com.example.cloudBalanceBackend.model.RevokedToken;
import com.example.cloudBalanceBackend.repository.RevokedTokenRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RevokedTokenRepository revokedTokenRepo;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, RevokedTokenRepository revokedTokenRepo) {
        this.jwtUtil = jwtUtil;
        this.revokedTokenRepo = revokedTokenRepo;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(7);
        try {
            Optional<RevokedToken> revoked = revokedTokenRepo.findByToken(token);
            if (revoked.isPresent()) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"error\":\"Token revoked\"}");
                return;
            }
            var jws = jwtUtil.validate(token);
            Claims claims = jws.getBody();
            String subject = claims.getSubject();
            String role = (String) claims.get("role");
            var auth = new UsernamePasswordAuthenticationToken(subject, null,
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Token expired\"}");
            return;
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Invalid token\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}