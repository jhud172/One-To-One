package uk.ac.cf._5.group14.One_To_One.DevMode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

@Service
@Slf4j
public class DevModePageAccessService {

    private static final String RESTRICTED_MESSAGE =
            "This feature is temporarily unavailable during development. Please check back later.";

    private final DevModePageSettingRepository settingRepository;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public DevModePageAccessService(DevModePageSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public DevModeHubView buildHubView(org.springframework.security.core.Authentication authentication) {
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        boolean clientAccount = authenticated && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_CLIENT".equals(authority.getAuthority()));
        Map<String, DevModePageAccessMode> overrides = loadOverrides();
        Map<DevModePageSection, List<DevModePageHubCard>> grouped = new EnumMap<>(DevModePageSection.class);
        grouped.put(DevModePageSection.PUBLIC, new ArrayList<>());
        grouped.put(DevModePageSection.LOGIN_REQUIRED, new ArrayList<>());
        grouped.put(DevModePageSection.RESTRICTED, new ArrayList<>());

        for (DevModePageDefinition definition : definitions()) {
            DevModePageAccessMode mode = resolveMode(definition, overrides);
            DevModePageSection section = mode == DevModePageAccessMode.ENABLED
                    ? definition.defaultSection()
                    : DevModePageSection.RESTRICTED;
            grouped.get(section).add(toHubCard(definition, mode, authenticated, clientAccount));
        }

        return new DevModeHubView(
                grouped.get(DevModePageSection.PUBLIC),
                grouped.get(DevModePageSection.LOGIN_REQUIRED),
                grouped.get(DevModePageSection.RESTRICTED));
    }

    public List<DevModePageAdminRow> buildAdminRows() {
        Map<String, DevModePageAccessMode> overrides = loadOverrides();
        return definitions().stream()
                .map(definition -> {
                    DevModePageAccessMode mode = resolveMode(definition, overrides);
                    DevModePageSection effectiveSection = mode == DevModePageAccessMode.ENABLED
                            ? definition.defaultSection()
                            : DevModePageSection.RESTRICTED;
                    return new DevModePageAdminRow(
                            definition.key(),
                            definition.title(),
                            definition.path(),
                            definition.description(),
                            labelForSection(definition.defaultSection()),
                            mode.name(),
                            labelForSection(effectiveSection),
                            toneForSection(effectiveSection),
                            labelForSection(effectiveSection),
                            summaryForMode(mode));
                })
                .collect(Collectors.toList());
    }

    public DevModeAdminSummary buildAdminSummary() {
        Map<String, DevModePageAccessMode> overrides = loadOverrides();
        long enabledCount = definitions().stream()
                .filter(definition -> resolveMode(definition, overrides) == DevModePageAccessMode.ENABLED)
                .count();
        long disabledCount = definitions().stream()
                .filter(definition -> resolveMode(definition, overrides) == DevModePageAccessMode.DISABLED)
                .count();
        long restrictedCount = definitions().stream()
                .filter(definition -> resolveMode(definition, overrides) == DevModePageAccessMode.RESTRICTED)
                .count();
        return new DevModeAdminSummary(enabledCount, disabledCount, restrictedCount);
    }

    public Optional<RestrictedRedirect> resolveRestrictedRedirect(String requestPath) {
        String normalized = normalisePath(requestPath);
        return definitions().stream()
                .filter(definition -> matches(definition, normalized))
                .findFirst()
                .flatMap(definition -> {
                    DevModePageAccessMode mode = resolveMode(definition, loadOverrides());
                    if (!mode.blocksAccess()) {
                        return Optional.empty();
                    }
                    return Optional.of(new RestrictedRedirect(definition.key(),
                            "/dev-mode/restricted?pageKey=" + urlEncode(definition.key())));
                });
    }

    public DevModeRestrictedNotice resolveRestrictedNotice(String pageKey) {
        DevModePageDefinition definition = definitions().stream()
                .filter(candidate -> candidate.key().equals(pageKey))
                .findFirst()
                .orElse(null);
        if (definition == null) {
            return new DevModeRestrictedNotice(
                    "Restricted Feature",
                    "/dev-mode",
                    "shield",
                    RESTRICTED_MESSAGE,
                    "The requested route is not available in the current development configuration.");
        }

        DevModePageAccessMode mode = resolveMode(definition, loadOverrides());
        String secondaryCopy = mode == DevModePageAccessMode.DISABLED
                ? "An admin has disabled access to this page for the current testing session."
                : "This page is currently marked as restricted in the Dev Hub configuration.";

        return new DevModeRestrictedNotice(
                definition.title(),
                definition.path(),
                definition.iconKey(),
                RESTRICTED_MESSAGE,
                secondaryCopy);
    }

    public void updateMode(String pageKey, DevModePageAccessMode accessMode) {
        DevModePageDefinition definition = definitions().stream()
                .filter(candidate -> candidate.key().equals(pageKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown development page key."));

        try {
            DevModePageSetting setting = settingRepository.findByPageKey(definition.key())
                    .orElseGet(() -> new DevModePageSetting(definition.key(), accessMode));
            setting.setAccessMode(accessMode);
            settingRepository.save(setting);
        } catch (DataAccessException ex) {
            log.warn("Dev mode page settings table is unavailable; unable to update page {}.", definition.key(), ex);
            throw new IllegalStateException("Dev mode page settings are temporarily unavailable.", ex);
        }
    }

    public boolean hasPage(String pageKey) {
        return definitions().stream().anyMatch(definition -> definition.key().equals(pageKey));
    }

    private DevModePageHubCard toHubCard(DevModePageDefinition definition,
                                         DevModePageAccessMode mode,
                                         boolean authenticated, boolean clientAccount) {
        DevModePageSection section = mode == DevModePageAccessMode.ENABLED
                ? definition.defaultSection()
                : DevModePageSection.RESTRICTED;

        String href;
        String availabilityCopy;
        if (mode.blocksAccess()) {
            href = "/dev-mode/restricted?pageKey=" + urlEncode(definition.key());
            availabilityCopy = mode == DevModePageAccessMode.DISABLED
                    ? "Disabled by admin configuration"
                    : "Restricted during development";
        } else if (definition.defaultSection() == DevModePageSection.PUBLIC) {
            href = definition.path();
            availabilityCopy = "Open now";
        } else if (authenticated && "client-trainers".equals(definition.key()) && !clientAccount) {
            href = "/dashboard";
            availabilityCopy = "Client account required";
        } else if (authenticated) {
            href = definition.path();
            availabilityCopy = "Your account permissions apply";
        } else {
            href = "/login?next=" + urlEncode(definition.path());
            availabilityCopy = "Sign in to access";
        }

        return new DevModePageHubCard(
                definition.key(),
                definition.title(),
                definition.description(),
                definition.path(),
                href,
                definition.iconKey(),
                toneForSection(section),
                labelForSection(section),
                availabilityCopy);
    }

    private DevModePageAccessMode resolveMode(DevModePageDefinition definition,
                                              Map<String, DevModePageAccessMode> overrides) {
        return overrides.getOrDefault(definition.key(), definition.defaultMode());
    }

    private Map<String, DevModePageAccessMode> loadOverrides() {
        try {
            return settingRepository.findAll().stream()
                    .collect(Collectors.toMap(DevModePageSetting::getPageKey, DevModePageSetting::getAccessMode,
                            (left, right) -> right, LinkedHashMap::new));
        } catch (DataAccessException ex) {
            log.warn("Dev mode page settings table is unavailable; falling back to default page access modes.", ex);
            return Map.of();
        }
    }

    private boolean matches(DevModePageDefinition definition, String requestPath) {
        return definition.requestPatterns().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, requestPath));
    }

    private String normalisePath(String requestPath) {
        if (requestPath == null || requestPath.isBlank()) {
            return "/";
        }
        String normalized = requestPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String labelForSection(DevModePageSection section) {
        return switch (section) {
            case PUBLIC -> "Public";
            case LOGIN_REQUIRED -> "Login Required";
            case RESTRICTED -> "Restricted";
        };
    }

    private String toneForSection(DevModePageSection section) {
        return switch (section) {
            case PUBLIC -> "public";
            case LOGIN_REQUIRED -> "login";
            case RESTRICTED -> "restricted";
        };
    }

    private String summaryForMode(DevModePageAccessMode mode) {
        return switch (mode) {
            case ENABLED -> "Available in its normal Dev Hub section";
            case DISABLED -> "Blocked and shown as restricted";
            case RESTRICTED -> "Marked unavailable for testers";
        };
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private List<DevModePageDefinition> definitions() {
        return PAGE_DEFINITIONS;
    }

    private static final List<DevModePageDefinition> PAGE_DEFINITIONS = List.of(
            new DevModePageDefinition("home", "Home", "Landing page and current product entry point.", "/", "home",
                    DevModePageSection.PUBLIC, DevModePageAccessMode.ENABLED, List.of("/")),
            new DevModePageDefinition("about", "About", "Platform overview and mission details.", "/about", "info",
                    DevModePageSection.PUBLIC, DevModePageAccessMode.ENABLED, List.of("/about")),
            new DevModePageDefinition("pricing", "Pricing", "Subscription plans and feature comparison.", "/pricing", "card",
                    DevModePageSection.PUBLIC, DevModePageAccessMode.ENABLED, List.of("/pricing", "/pricing/**")),
            new DevModePageDefinition("faq", "FAQ", "Common questions about the current platform experience.", "/faq", "faq",
                    DevModePageSection.PUBLIC, DevModePageAccessMode.ENABLED, List.of("/faq")),
            new DevModePageDefinition("explore", "Explore", "Browse public trainers and public discovery surfaces.", "/explore", "search",
                    DevModePageSection.PUBLIC, DevModePageAccessMode.ENABLED, List.of("/explore")),
            new DevModePageDefinition("signup", "Sign Up", "Create a fresh test account and try onboarding.", "/signup", "user-plus",
                    DevModePageSection.PUBLIC, DevModePageAccessMode.ENABLED, List.of("/signup", "/signup/**")),
            new DevModePageDefinition("privacy", "Privacy Policy", "Read platform privacy and data handling notes.", "/policies/privacy", "shield",
                    DevModePageSection.PUBLIC, DevModePageAccessMode.ENABLED, List.of("/policies/privacy")),
            new DevModePageDefinition("terms", "Terms of Service", "Review platform usage terms and conditions.", "/policies/terms", "file",
                    DevModePageSection.PUBLIC, DevModePageAccessMode.ENABLED, List.of("/policies/terms")),

            new DevModePageDefinition("dashboard", "Dashboard", "Open the main signed-in dashboard experience.", "/dashboard", "layout",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.ENABLED, List.of("/dashboard", "/dashboard/**", "/client/dashboard", "/client/dashboard/**")),
            new DevModePageDefinition("calendar", "Calendar", "View planner, tasks, and scheduling pages.", "/calendar", "calendar",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.ENABLED, List.of("/calendar", "/calendar/**")),
            new DevModePageDefinition("goals", "Goals", "Track personal goals and linked progress.", "/goals", "target",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.ENABLED, List.of("/goals", "/goals/**")),
            new DevModePageDefinition("workouts", "Workouts", "Access workout templates, sessions, and logs.", "/workouts", "dumbbell",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.ENABLED, List.of("/workouts", "/workouts/**")),
            new DevModePageDefinition("profile", "Profile", "Edit profile details and account presentation.", "/profile", "user",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.ENABLED, List.of("/profile", "/profile/**")),
            new DevModePageDefinition("messages", "Messages", "Open the signed-in messaging hub.", "/inbox", "message",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.ENABLED, List.of("/inbox", "/inbox/**")),
            new DevModePageDefinition("leaderboard", "Leaderboard", "Levels and points leaderboard surface.", "/levels", "trophy",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.RESTRICTED, List.of("/levels", "/levels/**")),
            new DevModePageDefinition("vault", "Training Vault", "Saved notes and personal vault content.", "/vault", "archive",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.RESTRICTED, List.of("/vault", "/vault/**")),
            new DevModePageDefinition("client-trainers", "Client Trainers", "Trainer matching and request flow for signed-in clients.", "/client/trainers", "users",
                    DevModePageSection.LOGIN_REQUIRED, DevModePageAccessMode.ENABLED, List.of("/client/trainers", "/client/trainers/**"))
    );

    private record DevModePageDefinition(String key,
                                         String title,
                                         String description,
                                         String path,
                                         String iconKey,
                                         DevModePageSection defaultSection,
                                         DevModePageAccessMode defaultMode,
                                         List<String> requestPatterns) {
    }

    public record DevModeHubView(List<DevModePageHubCard> publicPages,
                                 List<DevModePageHubCard> loginRequiredPages,
                                 List<DevModePageHubCard> restrictedPages) {
    }

    public record DevModePageHubCard(String key,
                                     String title,
                                     String description,
                                     String path,
                                     String href,
                                     String iconKey,
                                     String statusTone,
                                     String statusLabel,
                                     String availabilityCopy) {
    }

    public record DevModePageAdminRow(String key,
                                      String title,
                                      String path,
                                      String description,
                                      String defaultStatusLabel,
                                      String currentMode,
                                      String effectiveSectionLabel,
                                      String effectiveStatusTone,
                                      String effectiveStatusLabel,
                                      String modeSummary) {
    }

    public record DevModeAdminSummary(long enabledCount, long disabledCount, long restrictedCount) {
    }

    public record DevModeRestrictedNotice(String title,
                                          String path,
                                          String iconKey,
                                          String primaryMessage,
                                          String secondaryMessage) {
    }

    public record RestrictedRedirect(String pageKey, String redirectPath) {
    }
}
