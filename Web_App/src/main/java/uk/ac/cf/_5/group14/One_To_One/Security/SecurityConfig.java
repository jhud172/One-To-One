package uk.ac.cf._5.group14.One_To_One.Security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.session.InvalidSessionStrategy;

import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;
import uk.ac.cf._5.group14.One_To_One.DevMode.DevModePageRestrictionFilter;
import uk.ac.cf._5.group14.One_To_One.Security.SocialAuth.SocialAuthAvailabilityService;
import uk.ac.cf._5.group14.One_To_One.Security.SocialAuth.SocialOAuth2UserService;
import uk.ac.cf._5.group14.One_To_One.Security.SocialAuth.SocialOidcUserService;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Autowired
    private DevModeProperties devModeProperties;

    private static final String[] ENDPOINTS_WHITELIST = {
            "/img/**",
            "/models/one-to-one/**",
            "/css/**",
            "/js/**",
            "/webjars/**",
            "/favicon.ico",
            "/static/**",
            "/uploads/profile/**",
            "/uploads/merch/**",
            "/",
            "/home-public",
            "/about",
            "/faq",
            "/birthday/mission-vi",
            "/pricing",
            "/pricing/**",
            "/explore",
            "/merch",
            "/support",
            "/support/feedback",
            "/u/**",
            "/error/**",
            "/login/**",
            "/oauth2/**",
            "/auth/social/**",
            "/dev-mode/**",
            "/access-denied/**",
                "/signup/**",
                "/forgot-password",
                "/reset-password",
                    "/policies/**",
                    "/verify/email",
                    "/verify/email/code",
                    "/verify/email/send",
            "/verify/email/confirm",
                    "/api/mobile/auth/**"
    };
    
    /*
     * DEVELOPMENT MODE:
     * When DEV_MODE environment variable is set to "true":
     * - GET /login keeps the normal login form and adds development messaging
     * - Home page displays a development mode indicator badge
     * - Public pages are freely accessible without login
     * - Auth pages (dashboard, calendar, workouts, goals, profile) still require login
     *   but are fully functional once the user signs in via /login
     *
     * To enable dev mode:
     * 1. Set environment variable: DEV_MODE=true
     * 2. Restart the application
     *
     * To disable dev mode:
     * 1. Set environment variable: DEV_MODE=false (or unset)
     * 2. Restart the application
    */

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain birthdayExperienceSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(
                        "/birthday/**",
                        "/css/birthday/**",
                        "/js/birthday/**",
                        "/img/birthday/**"
                )
                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                .requestCache(cache -> cache.disable())
                .securityContext(context -> context.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   LoginThrottleFilter loginThrottleFilter,
                                                   ObjectProvider<PrivilegedAuditFilter> privilegedAuditFilterProvider,
                                                   DevModePageRestrictionFilter devModePageRestrictionFilter,
                                                   RoleAwareAuthenticationProvider roleAwareAuthenticationProvider,
                                                   LoginRequestDetailsSource loginRequestDetailsSource,
                                                   CustomAuthenticationFailureHandler failureHandler,
                                                   CustomAuthenticationSuccessHandler successHandler,
                                                   SocialAuthAvailabilityService socialAuthAvailabilityService,
                                                   SocialOAuth2UserService socialOAuth2UserService,
                                                   SocialOidcUserService socialOidcUserService,
                                                   ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
                                                   LogoutHandler logoutHandler,
                                                   AccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .authorizeHttpRequests(request -> {
                        if (devModeProperties.isDevMode()) {
                            // DEV MODE: Public pages are freely accessible without login.
                            // Authenticated pages (dashboard, calendar, workouts, goals, profile)
                            // require login but are fully functional once signed in.
                            // Restricted sections keep the same auth/role rules as production.
                            request
                            // Static assets and public pages: always open
                            .requestMatchers(ENDPOINTS_WHITELIST).permitAll()
                            .requestMatchers(HttpMethod.GET, "/trainers/*/profile").permitAll()
                            .requestMatchers("/reviews/**").authenticated()
                            .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                            .requestMatchers("/api/mobile/**").permitAll()
                            .requestMatchers(HttpMethod.POST, "/chat/ask").permitAll()
                            .requestMatchers("/dashboard/public", "/client/dashboard/public").permitAll()
                            .requestMatchers("/confirm-logout").authenticated()
                            .requestMatchers("/verify/phone/**").authenticated()
                            // Leaderboard: keep protected — not open in dev mode
                            .requestMatchers("/levels/**").authenticated()
                            // Trainers area: keep role requirements
                            .requestMatchers("/trainer/**").hasRole("TRAINER")
                            .requestMatchers("/gym/**").hasRole("GYM_ADMIN")
                            .requestMatchers("/super-admin/**").hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                            .requestMatchers("/client/trainers", "/client/trainers/**").hasRole("CLIENT")
                            .requestMatchers("/inbox", "/inbox/**", "/messages/**", "/client/messages", "/client/messages/**").authenticated()
                            .requestMatchers("/chat", "/chat/**", "/chatv2/**").authenticated()
                            .requestMatchers("/admin/gym-applications", "/admin/gym-applications/**").hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                            .requestMatchers("/admin/dashboard", "/admin/feedback", "/admin/feedback/**", "/admin/outreach/**", "/admin/dev-pages/**")
                            .hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                            .requestMatchers("/admin/**").hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                            .requestMatchers("/trainers/**").hasRole("CLIENT")
                            // Training Vault: keep protected
                            .requestMatchers("/vault/**").authenticated()
                            // Core authenticated pages: require login in dev mode
                            .requestMatchers("/dashboard", "/dashboard/**", "/client/dashboard", "/client/dashboard/**").authenticated()
                            .requestMatchers("/calendar", "/calendar/**").authenticated()
                            .requestMatchers("/workouts", "/workouts/**").authenticated()
                            .requestMatchers("/workout-session", "/workout-session/**").authenticated()
                            .requestMatchers("/workout-management", "/workout-management/**").authenticated()
                            .requestMatchers("/goals", "/goals/**").authenticated()
                            .requestMatchers("/profile", "/profile/**").authenticated()
                            .requestMatchers("/merch/**").authenticated()
                            // All other routes: open for dev browsing
                            .anyRequest().permitAll();
                        } else {
                            // Public profile reads are separate from protected coaching/review actions.
                            request
                            .requestMatchers(ENDPOINTS_WHITELIST).permitAll()
                            .requestMatchers(HttpMethod.GET, "/trainers/*/profile").permitAll()
                            .requestMatchers("/reviews/**").authenticated()
                            .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                            .requestMatchers("/api/mobile/**").permitAll()
                            .requestMatchers(HttpMethod.POST, "/chat/ask").permitAll()
                            .requestMatchers("/dashboard/public", "/client/dashboard/public").permitAll()
                            .requestMatchers("/confirm-logout").authenticated()
                            .requestMatchers("/verify/phone/**").authenticated()
                            .requestMatchers("/trainer/**").hasRole("TRAINER")
                            .requestMatchers("/gym/**").hasRole("GYM_ADMIN")
                            .requestMatchers("/super-admin/**").hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                            .requestMatchers("/client/**").hasRole("CLIENT")
                            .requestMatchers("/trainers/**").hasRole("CLIENT")
                            .requestMatchers("/admin/gym-applications", "/admin/gym-applications/**").hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                            .requestMatchers("/admin/dashboard", "/admin/feedback", "/admin/feedback/**", "/admin/outreach/**", "/admin/dev-pages/**")
                            .hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                            .requestMatchers("/admin/**").hasAnyRole("PLATFORM_ADMIN", "SUPER_ADMIN")
                            .requestMatchers("/merch/**").authenticated()
                            .requestMatchers("/dashboard", "/dashboard/**", "/client/dashboard", "/client/dashboard/**").authenticated()
                            .requestMatchers("/calendar", "/calendar/**").authenticated()
                            .requestMatchers("/workouts", "/workouts/**").authenticated()
                            .requestMatchers("/workout-session", "/workout-session/**").authenticated()
                            .requestMatchers("/workout-management", "/workout-management/**").authenticated()
                            .requestMatchers("/goals", "/goals/**").authenticated()
                            .requestMatchers("/profile", "/profile/**").authenticated()
                            .anyRequest().authenticated();
                        }
                })

                .formLogin(form -> form.loginPage("/login")
                    .permitAll()
                    .usernameParameter("username")
                    .passwordParameter("password")
                    .authenticationDetailsSource(loginRequestDetailsSource)
                    .failureHandler(failureHandler)
                    .successHandler(successHandler));

            http.csrf(csrf -> csrf.ignoringRequestMatchers(
                    "/api/mobile/**",
                    "/pricing/webhook/stripe"));

            if (clientRegistrationRepositoryProvider.getIfAvailable() != null && socialAuthAvailabilityService.hasEnabledProviders()) {
                http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .failureUrl("/login?error=social")
                    .userInfoEndpoint(userInfo -> userInfo
                        .userService(socialOAuth2UserService)
                        .oidcUserService(socialOidcUserService))
                    .successHandler(successHandler));
            }

            http.logout((l) -> l
                    .logoutUrl("/logout")
                    .addLogoutHandler(logoutHandler)
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID"))
                .sessionManagement(session -> session
                    .invalidSessionStrategy(invalidSessionStrategy()))
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(accessDeniedHandler)
                    .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                request -> request.getRequestURI() != null
                                        && (request.getRequestURI().startsWith("/chat/")
                                        || request.getRequestURI().startsWith("/chatv2/"))
                                        && (HttpMethod.GET.matches(request.getMethod())
                                        || HttpMethod.POST.matches(request.getMethod()))
                    ));
            http.authenticationProvider(roleAwareAuthenticationProvider);
            http.addFilterBefore(devModePageRestrictionFilter, UsernamePasswordAuthenticationFilter.class);
            http.addFilterBefore(loginThrottleFilter, UsernamePasswordAuthenticationFilter.class);
            PrivilegedAuditFilter privilegedAuditFilter = privilegedAuditFilterProvider.getIfAvailable();
            if (privilegedAuditFilter != null) {
                http.addFilterAfter(privilegedAuditFilter, AnonymousAuthenticationFilter.class);
            }
        return http.build();
    }

    @Bean
    public InvalidSessionStrategy invalidSessionStrategy() {
        return new CustomInvalidSessionStrategy();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return new CustomAccessDeniedHandler();
    }

    @Bean
    public LogoutHandler logoutHandler() {
        return new CustomLogoutHandler();
    }


}
