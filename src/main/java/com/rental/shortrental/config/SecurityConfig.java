package com.rental.shortrental.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import com.rental.shortrental.security.LandlordLoginSuccessHandler;
import com.rental.shortrental.security.RateLimitFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LandlordLoginSuccessHandler landlordLoginSuccessHandler,
            RateLimitFilter rateLimitFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/error",
                                "/css/**",
                                "/js/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/register").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/api/calendar-export/**").permitAll()
                        .requestMatchers("/guest/data/**").permitAll()
                        .requestMatchers("/api/public/guest-data/**").permitAll()
                        .requestMatchers("/api/public/documents/**").permitAll()
                        .requestMatchers("/public/test/**").permitAll()
                        .requestMatchers("/app", "/onboarding", "/dashboard", "/dashboard/**").hasRole("LANDLORD")
                        .requestMatchers("/api/landlord/**").hasRole("LANDLORD")
                        .requestMatchers("/api/integrations/avito/**").hasRole("LANDLORD")
                        .requestMatchers("/api/integrations/cian/**").hasRole("LANDLORD")
                        .requestMatchers("/api/integrations/partner/**").hasRole("LANDLORD")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler(landlordLoginSuccessHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .build();
    }
}
