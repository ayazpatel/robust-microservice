package com.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Value("${app.security.ceo.username:ceo}")
    private String ceoUsername;

    @Value("${app.security.ceo.password:{noop}password}")
    private String ceoPassword; // Accepts Spring Security password format like {noop}plain or {bcrypt}...

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        String pwd = ceoPassword;
        if (!pwd.startsWith("{")) {
            pwd = encoder.encode(pwd);
        }
        UserDetails user = User.withUsername(ceoUsername)
                .password(pwd)
                .roles("CEO")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/auth/**", "/api/**")
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; " +
                            "script-src 'self' https://cdn.jsdelivr.net; " +
                            "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; " +
                            "img-src 'self' data:; " +
                            "font-src 'self' data: https://cdn.jsdelivr.net; " +
                            "frame-ancestors 'none'; base-uri 'self'"))
                .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true))
            )
            .sessionManagement(sess -> sess
                .sessionFixation(sf -> sf.migrateSession())
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**", "/auth/**").permitAll()
                .requestMatchers("/api/**").hasRole("CEO")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/admins", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout", "GET"))
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
