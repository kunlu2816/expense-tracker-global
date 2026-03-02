package com.example.expense_tracking.config;

import com.example.expense_tracking.entity.User;
import com.example.expense_tracking.repository.UserRepository;
import com.example.expense_tracking.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;

        // 1. Check if the header contains a Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract the token (Remove "Bearer " prefix)
        jwt = authHeader.substring(7);

        // 3. Extract email from token
        // Using extractUsername method in JwtUtils
        userEmail = jwtUtils.extractUsername(jwt);

        // 4. Check if user is not authenticated yet
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // Load user details from DB
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            // 5. Validate token and set authentication context
            if (jwtUtils.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // This is the line that make the user is officially "Logged In" for this request
                SecurityContextHolder.getContext().setAuthentication(authToken);

                // Update lastActiveAt if it's more than 1 hour old (throttle to avoid too many DB writes)
                if (userDetails instanceof User) {
                    User user = (User) userDetails;
                    if (user.getLastActiveAt() == null ||
                        user.getLastActiveAt().isBefore(LocalDateTime.now().minusHours(1))) {
                        user.setLastActiveAt(LocalDateTime.now());
                        userRepository.save(user);
                    }
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
