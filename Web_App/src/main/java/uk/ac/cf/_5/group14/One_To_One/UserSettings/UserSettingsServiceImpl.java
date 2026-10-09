package uk.ac.cf._5.group14.One_To_One.UserSettings;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.Config.SupportedLanguage;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

@Service("userSettingsService")
public class UserSettingsServiceImpl implements UserSettingsService {


    private static final List<String> ALLOWED_WEEKLY_METRICS = List.of(
        "WORKOUTS_COMPLETED",
        "WORKOUTS_REMAINING",
        "TASKS_COMPLETED",
        "TASKS_NOT_COMPLETED",
        "MEALS_LOGGED",
        "HABITS_COMPLETED",
        "HABITS_NOT_COMPLETED",
        "WEIGHT_TREND",
        "WORKOUT_STREAK",
        "NUTRITION_STREAK"
    );

    private static final List<String> DEFAULT_WEEKLY_METRICS = List.of(
        "WORKOUTS_COMPLETED",
        "MEALS_LOGGED",
        "HABITS_COMPLETED"
    );

    private static final List<String> ALLOWED_PROFILE_BANNER_THEMES = List.of(
        "NONE",
        "AURORA",
        "SUNSET",
        "OCEAN",
        "ROSE",
        "CARBON",
        "LAGOON",
        "MEADOW",
        "MIDNIGHT"
    );

    private static final List<String> ALLOWED_PROFILE_RING_STYLES = List.of(
        "NONE",
        "NEON_DUAL",
        "SOLAR_FLARE",
        "CRYSTAL",
        "STARRY_SPARK",
        "AURORA_PULSE",
        "COMET_TRAIL",
        "EMBER_CROWN",
        "KING_CROWN",
        "CYBER_ARMS",
        "UFO_BEAM"
    );

    private static final List<String> ALLOWED_PROFILE_CARD_BACK_STYLES = List.of(
        "NONE",
        "GLASS",
        "TOPO",
        "CARBON",
        "MATRIX",
        "NEBULA",
        "CIRCUIT",
        "SUNBURST",
        "RETRO_GRID"
    );

    private static final String DEFAULT_PROFILE_BANNER_THEME = "AURORA";
    private static final String DEFAULT_PROFILE_RING_STYLE = "NEON_DUAL";
    private static final String DEFAULT_PROFILE_CARD_BACK_STYLE = "GLASS";
    private static final String DEFAULT_PROFILE_TEXT_COLOR = "#F8FAFC";
    private static final String DEFAULT_PROFILE_BIO_TEXT_COLOR = "#CBD5E1";
    private static final WeatherTemperatureUnitPreference DEFAULT_WEATHER_TEMPERATURE_UNIT = WeatherTemperatureUnitPreference.CELSIUS;
    private static final WeatherDisplayModePreference DEFAULT_WEATHER_DISPLAY_MODE = WeatherDisplayModePreference.VISUAL;
    private static final TimeDisplayFormatPreference DEFAULT_TIME_DISPLAY_FORMAT = TimeDisplayFormatPreference.TWELVE_HOUR;
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    public UserSettingsServiceImpl(UserSettingsRepository userSettingsRepository, UserRepository userRepository) {
        this.userSettingsRepository = userSettingsRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserSettings getOrCreate(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        UserSettings settings = userSettingsRepository.findById(user.getId())
                .orElseGet(() -> {
                    UserSettings newSettings = new UserSettings();
                    newSettings.setUser(userRepository.getReferenceById(user.getId()));
                    newSettings.setLanguage(normalizeLanguage(null));
                    newSettings.setTheme(isDemoUser(user) ? ThemePreference.LIGHT : ThemePreference.SYSTEM);
                    newSettings.setEasyMode(false);
                    newSettings.setColorBlindMode(false);
                    newSettings.setDisabilityHearing(false);
                    newSettings.setDisabilityMobility(false);
                    newSettings.setDisabilityVision(false);
                    newSettings.setShareRecoverySignals(false);
                    newSettings.setShareNutritionSignals(false);
                    newSettings.setShareSleepSignals(false);
                    newSettings.setShareFatigueSignals(false);
                    newSettings.setShareWeightTrend(false);
                    newSettings.setDefaultSets(3);
                    newSettings.setDefaultRepMin(8);
                    newSettings.setDefaultRepMax(12);
                    newSettings.setPreferredEquipmentBodyweight(false);
                    newSettings.setPreferredEquipmentDumbbell(false);
                    newSettings.setPreferredEquipmentBarbell(false);
                    newSettings.setPreferredEquipmentMachine(false);
                    newSettings.setPreferredEquipmentBands(false);
                    newSettings.setPreferredEquipmentKettlebell(false);
                    newSettings.setMacroTargetCalories(null);
                    newSettings.setMacroTargetProtein(null);
                    newSettings.setMacroTargetCarbs(null);
                    newSettings.setMacroTargetFat(null);
                    newSettings.setWeeklySummaryMetrics(String.join(",", DEFAULT_WEEKLY_METRICS));
                    newSettings.setProfileBannerTheme(DEFAULT_PROFILE_BANNER_THEME);
                    newSettings.setProfileRingStyle(DEFAULT_PROFILE_RING_STYLE);
                    newSettings.setProfileCardBackStyle(DEFAULT_PROFILE_CARD_BACK_STYLE);
                    newSettings.setProfileTextColor(DEFAULT_PROFILE_TEXT_COLOR);
                    newSettings.setProfileBioTextColor(DEFAULT_PROFILE_BIO_TEXT_COLOR);
                    newSettings.setProfileMilestoneKeys("");
                    newSettings.setWeatherTemperatureUnit(DEFAULT_WEATHER_TEMPERATURE_UNIT);
                    newSettings.setWeatherDisplayMode(DEFAULT_WEATHER_DISPLAY_MODE);
                    newSettings.setTimeDisplayFormat(DEFAULT_TIME_DISPLAY_FORMAT);
                    return userSettingsRepository.save(newSettings);
                });

        String normalizedLanguage = normalizeLanguage(settings.getLanguage());
        if (!normalizedLanguage.equals(settings.getLanguage())) {
            settings.setLanguage(normalizedLanguage);
            settings = userSettingsRepository.save(settings);
        }

        // Backfill weekly summary defaults for existing rows created before this field existed.
        if (settings.getWeeklySummaryMetrics() == null || settings.getWeeklySummaryMetrics().isBlank()) {
            settings.setWeeklySummaryMetrics(String.join(",", DEFAULT_WEEKLY_METRICS));
            settings = userSettingsRepository.save(settings);
        }

        if (settings.getProfileBannerTheme() == null || settings.getProfileBannerTheme().isBlank()) {
            settings.setProfileBannerTheme(DEFAULT_PROFILE_BANNER_THEME);
            settings = userSettingsRepository.save(settings);
        }
        if (settings.getProfileRingStyle() == null || settings.getProfileRingStyle().isBlank()) {
            settings.setProfileRingStyle(DEFAULT_PROFILE_RING_STYLE);
            settings = userSettingsRepository.save(settings);
        }
        if (settings.getProfileCardBackStyle() == null || settings.getProfileCardBackStyle().isBlank()) {
            settings.setProfileCardBackStyle(DEFAULT_PROFILE_CARD_BACK_STYLE);
            settings = userSettingsRepository.save(settings);
        }
        if (settings.getProfileTextColor() == null || settings.getProfileTextColor().isBlank()) {
            settings.setProfileTextColor(DEFAULT_PROFILE_TEXT_COLOR);
            settings = userSettingsRepository.save(settings);
        }
        if (settings.getProfileBioTextColor() == null || settings.getProfileBioTextColor().isBlank()) {
            settings.setProfileBioTextColor(DEFAULT_PROFILE_BIO_TEXT_COLOR);
            settings = userSettingsRepository.save(settings);
        }
        if (settings.getProfileMilestoneKeys() == null) {
            settings.setProfileMilestoneKeys("");
            settings = userSettingsRepository.save(settings);
        }
        if (settings.getWeatherTemperatureUnit() == null) {
            settings.setWeatherTemperatureUnit(DEFAULT_WEATHER_TEMPERATURE_UNIT);
            settings = userSettingsRepository.save(settings);
        }
        if (settings.getWeatherDisplayMode() == null) {
            settings.setWeatherDisplayMode(DEFAULT_WEATHER_DISPLAY_MODE);
            settings = userSettingsRepository.save(settings);
        }
        if (settings.getTimeDisplayFormat() == null) {
            settings.setTimeDisplayFormat(DEFAULT_TIME_DISPLAY_FORMAT);
            settings = userSettingsRepository.save(settings);
        }
        return settings;
    }

    private boolean isDemoUser(User user) {
        if (user == null || user.getUsername() == null) {
            return false;
        }
        return user.getUsername().toLowerCase().contains("demo");
    }

    @Override
    @Transactional
    public UserSettings update(User user, String language, ThemePreference theme, boolean easyMode) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        settings.setLanguage(normalizeLanguage(language));
        if (theme != null) {
            settings.setTheme(theme);
        }
        settings.setEasyMode(easyMode);

        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings updateWeatherPreferences(User user,
                                                 WeatherTemperatureUnitPreference weatherTemperatureUnit,
                                                 WeatherDisplayModePreference weatherDisplayMode) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        settings.setWeatherTemperatureUnit(weatherTemperatureUnit != null ? weatherTemperatureUnit : DEFAULT_WEATHER_TEMPERATURE_UNIT);
        settings.setWeatherDisplayMode(weatherDisplayMode != null ? weatherDisplayMode : DEFAULT_WEATHER_DISPLAY_MODE);
        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings updateTimeDisplayPreference(User user, TimeDisplayFormatPreference timeDisplayFormat) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        settings.setTimeDisplayFormat(timeDisplayFormat != null ? timeDisplayFormat : DEFAULT_TIME_DISPLAY_FORMAT);
        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public boolean isQuickPreferencesCompleted(User user) {
        UserSettings settings = getOrCreate(user);
        return settings != null && settings.isQuickPreferencesCompleted();
    }

    @Override
    @Transactional
    public UserSettings updateQuickPreferencesCompleted(User user, boolean completed) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        settings.setQuickPreferencesCompleted(completed);
        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings updateCalendarPreferences(
            User user,
            CalendarTaskOrderingPreference calendarTaskOrdering,
            CalendarTaskLayoutPreference calendarTaskLayout
    ) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        if (calendarTaskOrdering != null) {
            settings.setCalendarTaskOrdering(calendarTaskOrdering);
        }
        if (calendarTaskLayout != null) {
            settings.setCalendarTaskLayout(calendarTaskLayout);
        }

        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings updateWorkoutCalendarPreferences(User user, CalendarWorkoutOrderingPreference calendarWorkoutOrdering) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        if (calendarWorkoutOrdering != null) {
            settings.setCalendarWorkoutOrdering(calendarWorkoutOrdering);
        }

        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings updateCalendarViewPreference(User user, CalendarViewPreference calendarViewPreference) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        if (calendarViewPreference != null) {
            settings.setCalendarViewPreference(calendarViewPreference);
        }

        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings updateAccessibility(User user,
                                            boolean colorBlindMode,
                                            boolean disabilityHearing,
                                            boolean disabilityMobility,
                                            boolean disabilityVision) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        settings.setColorBlindMode(colorBlindMode);
        settings.setDisabilityHearing(disabilityHearing);
        settings.setDisabilityMobility(disabilityMobility);
        settings.setDisabilityVision(disabilityVision);

        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings updateTrainerSharing(User user,
                                             boolean shareRecoverySignals,
                                             boolean shareNutritionSignals,
                                             boolean shareSleepSignals,
                                             boolean shareFatigueSignals,
                                             boolean shareWeightTrend) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        settings.setShareRecoverySignals(shareRecoverySignals);
        settings.setShareNutritionSignals(shareNutritionSignals);
        settings.setShareSleepSignals(shareSleepSignals);
        settings.setShareFatigueSignals(shareFatigueSignals);
        settings.setShareWeightTrend(shareWeightTrend);

        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings updateSmartDefaults(User user,
                                            Integer defaultSets,
                                            Integer defaultRepMin,
                                            Integer defaultRepMax,
                                            boolean preferredEquipmentBodyweight,
                                            boolean preferredEquipmentDumbbell,
                                            boolean preferredEquipmentBarbell,
                                            boolean preferredEquipmentMachine,
                                            boolean preferredEquipmentBands,
                                            boolean preferredEquipmentKettlebell,
                                            boolean preferredEquipmentCable,
                                            boolean preferredEquipmentPullupBar,
                                            boolean preferredEquipmentJumpRope,
                                            boolean preferredEquipmentMedicineBall,
                                            boolean preferredEquipmentFoamRoller,
                                            boolean preferredEquipmentTrx,
                                            boolean preferredEquipmentOther,
                                            String preferredEquipmentOtherSpecify,
                                            Integer macroTargetCalories,
                                            Integer macroTargetProtein,
                                            Integer macroTargetCarbs,
                                            Integer macroTargetFat,
                                            Set<String> weeklySummaryMetrics) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        int sets = clamp(defaultSets, 1, 20, settings.getDefaultSets());
        int repMin = clamp(defaultRepMin, 1, 30, settings.getDefaultRepMin());
        int repMax = clamp(defaultRepMax, repMin, 50, Math.max(repMin, settings.getDefaultRepMax()));

        settings.setDefaultSets(sets);
        settings.setDefaultRepMin(repMin);
        settings.setDefaultRepMax(repMax);
        settings.setPreferredEquipmentBodyweight(preferredEquipmentBodyweight);
        settings.setPreferredEquipmentDumbbell(preferredEquipmentDumbbell);
        settings.setPreferredEquipmentBarbell(preferredEquipmentBarbell);
        settings.setPreferredEquipmentMachine(preferredEquipmentMachine);
        settings.setPreferredEquipmentBands(preferredEquipmentBands);
        settings.setPreferredEquipmentKettlebell(preferredEquipmentKettlebell);
        settings.setPreferredEquipmentCable(preferredEquipmentCable);
        settings.setPreferredEquipmentPullupBar(preferredEquipmentPullupBar);
        settings.setPreferredEquipmentJumpRope(preferredEquipmentJumpRope);
        settings.setPreferredEquipmentMedicineBall(preferredEquipmentMedicineBall);
        settings.setPreferredEquipmentFoamRoller(preferredEquipmentFoamRoller);
        settings.setPreferredEquipmentTrx(preferredEquipmentTrx);
        settings.setPreferredEquipmentOther(preferredEquipmentOther);
        settings.setPreferredEquipmentOtherSpecify(preferredEquipmentOther ? preferredEquipmentOtherSpecify : null);
        settings.setMacroTargetCalories(normalizeOptional(macroTargetCalories, 0, 20000));
        settings.setMacroTargetProtein(normalizeOptional(macroTargetProtein, 0, 1000));
        settings.setMacroTargetCarbs(normalizeOptional(macroTargetCarbs, 0, 1000));
        settings.setMacroTargetFat(normalizeOptional(macroTargetFat, 0, 1000));
        settings.setWeeklySummaryMetrics(String.join(",", normalizeWeeklyMetricKeys(weeklySummaryMetrics)));

        return userSettingsRepository.save(settings);
    }

    @Override
    @Transactional
    public UserSettings resetSmartDefaults(User user) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        settings.setDefaultSets(3);
        settings.setDefaultRepMin(8);
        settings.setDefaultRepMax(12);
        settings.setPreferredEquipmentBodyweight(false);
        settings.setPreferredEquipmentDumbbell(false);
        settings.setPreferredEquipmentBarbell(false);
        settings.setPreferredEquipmentMachine(false);
        settings.setPreferredEquipmentBands(false);
        settings.setPreferredEquipmentKettlebell(false);
        settings.setPreferredEquipmentCable(false);
        settings.setPreferredEquipmentPullupBar(false);
        settings.setPreferredEquipmentJumpRope(false);
        settings.setPreferredEquipmentMedicineBall(false);
        settings.setPreferredEquipmentFoamRoller(false);
        settings.setPreferredEquipmentTrx(false);
        settings.setPreferredEquipmentOther(false);
        settings.setPreferredEquipmentOtherSpecify(null);
        settings.setMacroTargetCalories(null);
        settings.setMacroTargetProtein(null);
        settings.setMacroTargetCarbs(null);
        settings.setMacroTargetFat(null);
        settings.setWeeklySummaryMetrics(String.join(",", DEFAULT_WEEKLY_METRICS));

        return userSettingsRepository.save(settings);
    }

    private List<String> normalizeWeeklyMetricKeys(Set<String> selected) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (selected != null) {
            selected.stream()
                    .filter(key -> key != null && !key.isBlank())
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(ALLOWED_WEEKLY_METRICS::contains)
                    .limit(6)
                    .forEach(cleaned::add);
        }
        if (cleaned.isEmpty()) {
            cleaned.addAll(DEFAULT_WEEKLY_METRICS);
        }
        return cleaned.stream().collect(Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public UserSettings updateHideAiOneShotWarning(User user, boolean hide) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }
        settings.setHideAiOneShotWarning(hide);
        return userSettingsRepository.save(settings);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public UserSettings updatePreferredWorkoutTemplate(User user, Long templateId) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }
        settings.setPreferredWorkoutTemplateId(templateId);
        return userSettingsRepository.save(settings);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public UserSettings updateStickerPreferences(User user, StickerPackPreference stickerPack, int monthlyWorkoutTarget) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }
        if (stickerPack != null) {
            settings.setStickerPack(stickerPack);
        }
        int target = Math.min(Math.max(monthlyWorkoutTarget, 1), 31);
        settings.setMonthlyWorkoutTarget(target);
        return userSettingsRepository.save(settings);
    }

    private int clamp(Integer value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        return Math.min(Math.max(value, min), max);
    }

    private Integer normalizeOptional(Integer value, int min, int max) {
        if (value == null) {
            return null;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    @Override
    @Transactional
    public UserSettings updateProfileCustomizer(User user,
                                                String bannerTheme,
                                                String ringStyle,
                                                String cardBackStyle,
                                                String textColor,
                                                String generalTextColor,
                                                Set<String> milestoneKeys) {
        UserSettings settings = getOrCreate(user);
        if (settings == null) {
            return null;
        }

        settings.setProfileBannerTheme(normalizeKey(bannerTheme, ALLOWED_PROFILE_BANNER_THEMES, DEFAULT_PROFILE_BANNER_THEME));
        settings.setProfileRingStyle(normalizeKey(ringStyle, ALLOWED_PROFILE_RING_STYLES, DEFAULT_PROFILE_RING_STYLE));
        settings.setProfileCardBackStyle(normalizeKey(cardBackStyle, ALLOWED_PROFILE_CARD_BACK_STYLES, DEFAULT_PROFILE_CARD_BACK_STYLE));
        settings.setProfileTextColor(normalizeHexColor(textColor, DEFAULT_PROFILE_TEXT_COLOR));
        settings.setProfileBioTextColor(normalizeHexColor(generalTextColor, DEFAULT_PROFILE_BIO_TEXT_COLOR));
        settings.setProfileMilestoneKeys(String.join(",", normalizeMilestoneKeys(milestoneKeys)));

        return userSettingsRepository.save(settings);
    }

    private String normalizeKey(String value, List<String> allowed, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private String normalizeHexColor(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim();
        if (!HEX_COLOR_PATTERN.matcher(normalized).matches()) {
            return fallback;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeLanguage(String value) {
        return SupportedLanguage.normalizeCode(value);
    }

    private List<String> normalizeMilestoneKeys(Set<String> selected) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (selected != null) {
            selected.stream()
                    .filter(key -> key != null && !key.isBlank())
                    .map(String::trim)
                    .map(key -> key.toUpperCase(Locale.ROOT))
                    .limit(6)
                    .forEach(cleaned::add);
        }
        return cleaned.stream().collect(Collectors.toList());
    }
}
