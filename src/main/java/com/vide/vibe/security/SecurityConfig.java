package com.vide.vibe.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // enables @PreAuthorize
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // public browsing
                        .requestMatchers("/", "/explore/**", "/p/**", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/submit/**").permitAll() // anonymous submission flow, as today
                        .requestMatchers("/claim/**").permitAll()

                        // ── manage page is public read: guests can view + claim, but not edit ──
                        // Must come BEFORE the general "/apps/**" rule below — Spring Security
                        // uses first-match-wins, so these specific GET/POST exceptions need to
                        // be declared earlier or the blanket authenticated() rule would catch
                        // them first and bounce guests to the login page before the controller's
                        // own canEdit logic ever runs.
                        .requestMatchers(HttpMethod.GET, "/apps/*/manage").permitAll()
                        .requestMatchers(HttpMethod.POST, "/apps/*/claim/**").permitAll()

                        // admin-only management of taxonomy/reviews
                        .requestMatchers("/categories/**", "/review-categories/**", "/apps/*/reviews/**").hasRole("ADMIN")

                        // homepage layout: manager or admin
                        .requestMatchers("/admin/home-sections/**", "/api/site-config/**").hasAnyRole("MANAGER", "ADMIN")

                        // all other app management (editing info, categories, workflows, media, etc.)
                        // still requires login — ownership is further checked at the method level
                        // via @PreAuthorize(canEdit) in ManageController
                        .requestMatchers("/apps/**").authenticated()
                        .requestMatchers("/media/**").authenticated()

                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .defaultSuccessUrl("/", false)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/")
                )
                .csrf(AbstractHttpConfigurer::disable); // re-enable once your AJAX calls send the CSRF token; see note below

        return http.build();
    }
}