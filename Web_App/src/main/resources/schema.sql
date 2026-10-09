-- =========================
-- DROP (order matters)
-- =========================
DROP VIEW IF EXISTS user_authorities CASCADE;

DROP TABLE IF EXISTS privileged_audit_events CASCADE;
DROP TABLE IF EXISTS scheduled_job_locks CASCADE;
DROP TABLE IF EXISTS login_attempts CASCADE;
DROP TABLE IF EXISTS spring_session_attributes CASCADE;
DROP TABLE IF EXISTS spring_session CASCADE;
DROP TABLE IF EXISTS merch_order_items CASCADE;
DROP TABLE IF EXISTS merch_orders CASCADE;
DROP TABLE IF EXISTS saved_payment_methods CASCADE;
DROP TABLE IF EXISTS merch_products CASCADE;
DROP TABLE IF EXISTS price_change_events CASCADE;
DROP TABLE IF EXISTS gym_member_subscriptions CASCADE;
DROP TABLE IF EXISTS gym_membership_products CASCADE;

DROP TABLE IF EXISTS review_moderations CASCADE;
DROP TABLE IF EXISTS client_assessments CASCADE;
DROP TABLE IF EXISTS trainer_reviews CASCADE;
DROP TABLE IF EXISTS trainer_profiles CASCADE;
DROP TABLE IF EXISTS trainer_gym_affiliation_events CASCADE;
DROP TABLE IF EXISTS trainer_gym_affiliations CASCADE;
DROP TABLE IF EXISTS gym_profiles CASCADE;
DROP TABLE IF EXISTS trainer_verification_documents CASCADE;
DROP TABLE IF EXISTS trainer_verification_events CASCADE;
DROP TABLE IF EXISTS trainer_verification_requests CASCADE;

DROP TABLE IF EXISTS off_platform_payment_attempts CASCADE;
DROP TABLE IF EXISTS message_read_states CASCADE;
DROP TABLE IF EXISTS message_thread_messages CASCADE;
DROP TABLE IF EXISTS message_threads CASCADE;
DROP TABLE IF EXISTS notifications CASCADE;

DROP TABLE IF EXISTS message CASCADE;
DROP TABLE IF EXISTS conversation_participant CASCADE;
DROP TABLE IF EXISTS conversation CASCADE;
DROP TABLE IF EXISTS dashboard_layout CASCADE;
DROP TABLE IF EXISTS dev_mode_page_settings CASCADE;
DROP TABLE IF EXISTS user_settings CASCADE;
DROP TABLE IF EXISTS data_export_requests CASCADE;
DROP TABLE IF EXISTS user_health_conditions CASCADE;
DROP TABLE IF EXISTS assigned_workouts CASCADE;
DROP TABLE IF EXISTS assigned_schedules CASCADE;

DROP TABLE IF EXISTS selected_preferences CASCADE;
DROP TABLE IF EXISTS user_preference_conditions CASCADE;
DROP TABLE IF EXISTS user_preferences CASCADE;
DROP TABLE IF EXISTS record_condition CASCADE;
DROP TABLE IF EXISTS health_records CASCADE;
DROP TABLE IF EXISTS trainer_library_shared_templates CASCADE;
DROP TABLE IF EXISTS trainer_library_programme_notes CASCADE;
DROP TABLE IF EXISTS trainer_library_programme_days CASCADE;
DROP TABLE IF EXISTS trainer_library_programme_templates CASCADE;
DROP TABLE IF EXISTS trainer_library_workout_notes CASCADE;
DROP TABLE IF EXISTS trainer_library_workout_items CASCADE;
DROP TABLE IF EXISTS trainer_library_workout_templates CASCADE;
DROP TABLE IF EXISTS trainer_library_exercise_notes CASCADE;
DROP TABLE IF EXISTS trainer_library_exercises CASCADE;
DROP TABLE IF EXISTS coaching_phase_changes CASCADE;
DROP TABLE IF EXISTS weekly_check_ins CASCADE;
DROP TABLE IF EXISTS trainer_checkin_questions CASCADE;
DROP TABLE IF EXISTS trainer_schedule_template_entries CASCADE;
DROP TABLE IF EXISTS trainer_schedule_templates CASCADE;
DROP TABLE IF EXISTS weekly_summaries CASCADE;
DROP TABLE IF EXISTS user_streaks CASCADE;
DROP TABLE IF EXISTS trainer_client_links CASCADE;
DROP TABLE IF EXISTS goal_check_ins CASCADE;
DROP TABLE IF EXISTS goal_links CASCADE;
DROP TABLE IF EXISTS goals CASCADE;
DROP TABLE IF EXISTS platform_subscriptions CASCADE;
DROP TABLE IF EXISTS stripe_webhook_events CASCADE;
DROP TABLE IF EXISTS gym_subscriptions CASCADE;
DROP TABLE IF EXISTS password_reset_tokens CASCADE;
DROP TABLE IF EXISTS email_verification_tokens CASCADE;
DROP TABLE IF EXISTS phone_verification_codes CASCADE;
DROP TABLE IF EXISTS users_roles CASCADE;
DROP TABLE IF EXISTS roles CASCADE;
DROP TABLE IF EXISTS physical_condition_tag CASCADE;
DROP TABLE IF EXISTS physical_conditions CASCADE;
DROP TABLE IF EXISTS exercises_tags CASCADE;
DROP TABLE IF EXISTS preference_tag CASCADE;
DROP TABLE IF EXISTS preferences CASCADE;
DROP TABLE IF EXISTS tags CASCADE;
DROP TABLE IF EXISTS schedule_entries CASCADE;
DROP TABLE IF EXISTS schedule_applied CASCADE;
DROP TABLE IF EXISTS schedules CASCADE;
DROP TABLE IF EXISTS exercise_log CASCADE;
DROP TABLE IF EXISTS calendar_tasks CASCADE;
DROP TABLE IF EXISTS day_health CASCADE;
DROP TABLE IF EXISTS daily_nutrition_logs CASCADE;
DROP TABLE IF EXISTS daily_focus CASCADE;
DROP TABLE IF EXISTS adaptive_feedback CASCADE;
DROP TABLE IF EXISTS daily_completion CASCADE;
DROP TABLE IF EXISTS coach_action_logs CASCADE;
DROP TABLE IF EXISTS coach_messages CASCADE;
DROP TABLE IF EXISTS coach_conversations CASCADE;
DROP TABLE IF EXISTS blood_pressure_readings CASCADE;
DROP TABLE IF EXISTS daily_usage CASCADE;
DROP TABLE IF EXISTS chat_messages CASCADE;
DROP TABLE IF EXISTS chat_threads CASCADE;
DROP TABLE IF EXISTS chat_folders CASCADE;
DROP TABLE IF EXISTS ai_form_feedback CASCADE;
DROP TABLE IF EXISTS workout_session_sets CASCADE;
DROP TABLE IF EXISTS workout_session_exercises CASCADE;
DROP TABLE IF EXISTS workout_template_sessions CASCADE;
DROP TABLE IF EXISTS workout_ui_templates CASCADE;
DROP TABLE IF EXISTS day_optimisations CASCADE;
DROP TABLE IF EXISTS workout_set_videos CASCADE;
DROP TABLE IF EXISTS workout_set_logs CASCADE;
DROP TABLE IF EXISTS workout_player_sessions CASCADE;
DROP TABLE IF EXISTS workout_template_exercises CASCADE;
DROP TABLE IF EXISTS workout_templates CASCADE;
DROP TABLE IF EXISTS set_logs CASCADE;
DROP TABLE IF EXISTS exercise_sessions CASCADE;
DROP TABLE IF EXISTS vault_notes CASCADE;
DROP TABLE IF EXISTS workout_sessions CASCADE;
DROP TABLE IF EXISTS workouts_exercises CASCADE;
DROP TABLE IF EXISTS favourites CASCADE;
DROP TABLE IF EXISTS exercises CASCADE;
DROP TABLE IF EXISTS workouts CASCADE;
DROP TABLE IF EXISTS custom_exercises CASCADE;
DROP TABLE IF EXISTS notes CASCADE;
DROP TABLE IF EXISTS note_folders CASCADE;
DROP TABLE IF EXISTS user_points CASCADE;
DROP TABLE IF EXISTS schedule_occurrences CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- =========================
-- USERS
-- =========================
CREATE TABLE IF NOT EXISTS users
(
    id                  BIGSERIAL PRIMARY KEY,
    public_id           VARCHAR(36) NOT NULL,
    email               VARCHAR(100) NOT NULL,
    email_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at   TIMESTAMP    NULL,
    first_name          VARCHAR(100) NOT NULL,
    last_name           VARCHAR(100) NOT NULL,
    username            VARCHAR(100) NOT NULL,
    username_changed_at TIMESTAMP    NULL,
    bio                 VARCHAR(800) NULL,
    profile_image_url   VARCHAR(300) NULL,
    date_of_birth       DATE         NULL,
    phone_number        VARCHAR(30)  NULL,
    phone_country       VARCHAR(2)   NOT NULL DEFAULT 'GB',
    phone_verified      BOOLEAN      NOT NULL DEFAULT FALSE,
    phone_verified_at   TIMESTAMP    NULL,
    password            VARCHAR(500) NOT NULL,
    enabled             BOOLEAN      NOT NULL,
    subscription_status BOOLEAN      NOT NULL,

    role                VARCHAR(30)  NOT NULL DEFAULT 'CLIENT',
    gym_id              BIGINT       NULL,
    trainer_profile_id  BIGINT       NULL,
    trainer_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    has_seen_tutorial   BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Needed because users_roles.username references users.username
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_public_id UNIQUE (public_id)
);

-- =========================
-- NOTIFICATIONS
-- =========================
CREATE TABLE IF NOT EXISTS notifications
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL,
    type         VARCHAR(20) NOT NULL,
    title        VARCHAR(255),
    message      TEXT        NOT NULL,
    cta_url      VARCHAR(500),
    created_at   TIMESTAMP   NOT NULL,
    read_at      TIMESTAMP   NULL,
    dismissed_at TIMESTAMP   NULL,

    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS quick_action_definitions
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(120) NOT NULL,
    action_key VARCHAR(60),
    prompt VARCHAR(2000),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quick_action_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_quick_action_user
    ON quick_action_definitions (user_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_status
    ON notifications (user_id, read_at, dismissed_at);

-- =========================
-- PLATFORM SUBSCRIPTIONS
-- =========================
CREATE TABLE IF NOT EXISTS platform_subscriptions
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    plan                  VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    current_period_end    TIMESTAMP,
    cancel_at_period_end  BOOLEAN      NOT NULL DEFAULT FALSE,
    provider_customer_id  VARCHAR(200),
    provider_sub_id       VARCHAR(200),

    CONSTRAINT fk_platform_subscription_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_platform_subscription_user UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_platform_subscriptions_user
    ON platform_subscriptions (user_id);

CREATE TABLE IF NOT EXISTS stripe_webhook_events
(
    event_id      VARCHAR(255) PRIMARY KEY,
    event_type    VARCHAR(120) NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stripe_webhook_events_processed_at
    ON stripe_webhook_events (processed_at);

-- =========================
-- GYM SUBSCRIPTIONS (PLATFORM VIEW)
-- =========================
CREATE TABLE IF NOT EXISTS gym_subscriptions
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    gym_id                BIGINT       NOT NULL,
    product_id            BIGINT       NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    current_period_end    TIMESTAMP,
    cancel_at_period_end  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_gym_subscriptions_user
    ON gym_subscriptions (user_id);

CREATE INDEX IF NOT EXISTS idx_gym_subscriptions_gym
    ON gym_subscriptions (gym_id);

-- =========================
-- PASSWORD RESET TOKENS
-- =========================
CREATE TABLE IF NOT EXISTS password_reset_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(120) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,

    CONSTRAINT fk_password_reset_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_password_reset_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_user
    ON password_reset_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token
    ON password_reset_tokens (token);

-- =========================
-- EMAIL VERIFICATION TOKENS
-- =========================
CREATE TABLE IF NOT EXISTS email_verification_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(120) NOT NULL,
    code       VARCHAR(120),
    attempts   INT          NOT NULL DEFAULT 0,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_email_verification_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_email_verification_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_email_verification_user
    ON email_verification_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_email_verification_token
    ON email_verification_tokens (token);

-- =========================
-- PHONE VERIFICATION CODES
-- =========================
CREATE TABLE IF NOT EXISTS phone_verification_codes
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    code        VARCHAR(120) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at TIMESTAMP,

    CONSTRAINT fk_phone_verification_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_phone_verification_user
    ON phone_verification_codes (user_id);

-- =========================
-- SOCIAL AUTH IDENTITIES
-- =========================
CREATE TABLE IF NOT EXISTS user_social_identities
(
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL,
    provider              VARCHAR(30)  NOT NULL,
    provider_subject      VARCHAR(190) NOT NULL,
    provider_email        VARCHAR(255) NULL,
    provider_display_name VARCHAR(200) NULL,
    profile_image_url     VARCHAR(500) NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_social_identities_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_social_provider_subject
    ON user_social_identities (provider, provider_subject);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_social_user_provider
    ON user_social_identities (user_id, provider);

-- =========================
-- USER SETTINGS (preferences)
-- =========================
CREATE TABLE IF NOT EXISTS user_settings
(
    user_id    BIGINT PRIMARY KEY,
    language   VARCHAR(20)  NOT NULL DEFAULT 'en',
    theme      VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',
    easy_mode  BOOLEAN      NOT NULL DEFAULT FALSE,
    color_blind_mode BOOLEAN NOT NULL DEFAULT FALSE,
    disability_hearing BOOLEAN NOT NULL DEFAULT FALSE,
    disability_mobility BOOLEAN NOT NULL DEFAULT FALSE,
    disability_vision BOOLEAN NOT NULL DEFAULT FALSE,
    share_recovery_signals BOOLEAN NOT NULL DEFAULT FALSE,
    share_nutrition_signals BOOLEAN NOT NULL DEFAULT FALSE,
    share_sleep_signals BOOLEAN NOT NULL DEFAULT FALSE,
    share_fatigue_signals BOOLEAN NOT NULL DEFAULT FALSE,
    share_weight_trend BOOLEAN NOT NULL DEFAULT FALSE,
    calendar_task_ordering VARCHAR(30) NOT NULL DEFAULT 'CHRONOLOGICAL',
    calendar_task_layout   VARCHAR(30) NOT NULL DEFAULT 'COMBINED_LIST',
    calendar_workout_ordering VARCHAR(30) NOT NULL DEFAULT 'SCHEDULE_ORDER',
    calendar_view_preference VARCHAR(10) NOT NULL DEFAULT 'MONTH',
    default_sets INT NOT NULL DEFAULT 3,
    default_rep_min INT NOT NULL DEFAULT 8,
    default_rep_max INT NOT NULL DEFAULT 12,
    preferred_equipment_bodyweight BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_dumbbell BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_barbell BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_machine BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_bands BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_kettlebell BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_cable BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_pullup_bar BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_jump_rope BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_medicine_ball BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_foam_roller BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_trx BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_other BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_equipment_other_specify VARCHAR(200) NULL,
    macro_target_calories INT NULL,
    macro_target_protein INT NULL,
    macro_target_carbs INT NULL,
    macro_target_fat INT NULL,
    quiet_hours_start TIME NULL,
    quiet_hours_end   TIME NULL,
    preferred_workout_template_id BIGINT NULL,
    hide_ai_one_shot_warning BOOLEAN NOT NULL DEFAULT FALSE,
    sticker_pack VARCHAR(20) NOT NULL DEFAULT 'STARS',
    monthly_workout_target INT NOT NULL DEFAULT 12,
    weekly_summary_metrics VARCHAR(500) NULL,
    profile_banner_theme VARCHAR(40) NULL,
    profile_ring_style VARCHAR(40) NULL,
    profile_card_back_style VARCHAR(40) NULL,
    profile_text_color VARCHAR(7) NULL,
    profile_bio_text_color VARCHAR(7) NULL,
    profile_milestone_keys VARCHAR(500) NULL,
    dashboard_immersion_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dashboard_weather_preset VARCHAR(20) NULL DEFAULT 'auto',
    weather_temperature_unit VARCHAR(12) NOT NULL DEFAULT 'CELSIUS',
    weather_display_mode VARCHAR(12) NOT NULL DEFAULT 'VISUAL',
    time_display_format VARCHAR(18) NOT NULL DEFAULT 'TWELVE_HOUR',
    quick_preferences_completed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_settings_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS weather_temperature_unit VARCHAR(12) NOT NULL DEFAULT 'CELSIUS';

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS weather_display_mode VARCHAR(12) NOT NULL DEFAULT 'VISUAL';

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS time_display_format VARCHAR(18) NOT NULL DEFAULT 'TWELVE_HOUR';

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS quick_preferences_completed BOOLEAN NOT NULL DEFAULT FALSE;

-- =========================
-- DEV MODE PAGE ACCESS
-- =========================
CREATE TABLE IF NOT EXISTS dev_mode_page_settings
(
    id BIGSERIAL PRIMARY KEY,
    page_key VARCHAR(80) NOT NULL UNIQUE,
    access_mode VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- =========================
-- HEALTH CONDITIONS (PERMANENT + TIMED)
-- =========================
CREATE TABLE IF NOT EXISTS user_health_conditions
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    condition_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    duration_days INT NULL,
    follow_up_sent_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_health_conditions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_health_conditions_user
    ON user_health_conditions (user_id, condition_type);

-- =========================
-- DATA EXPORT REQUESTS
-- =========================
CREATE TABLE IF NOT EXISTS data_export_requests
(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_data_export_requests_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_data_export_requests_user
    ON data_export_requests (user_id, requested_at);

-- =========================
-- BEHAVIOUR MEMORY (non-sensitive aggregates)
-- =========================
CREATE TABLE IF NOT EXISTS behaviour_memory
(
    user_id BIGINT PRIMARY KEY,
    as_of_date DATE NOT NULL,
    window_days INT NOT NULL,

    green_days INT NOT NULL DEFAULT 0,
    orange_days INT NOT NULL DEFAULT 0,
    red_days INT NOT NULL DEFAULT 0,
    grey_days INT NOT NULL DEFAULT 0,

    avg_completion_percentage INT NOT NULL DEFAULT 0,
    avg_tasks_per_day DOUBLE PRECISION NOT NULL DEFAULT 0,
    high_load_days INT NOT NULL DEFAULT 0,
    time_pressure_score INT NOT NULL DEFAULT 0,

    last_ai_reference_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_behaviour_memory_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- DASHBOARD LAYOUT
-- =========================
CREATE TABLE IF NOT EXISTS dashboard_layout
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    module_key VARCHAR(80)  NOT NULL,
    sort_index INT          NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dashboard_layout_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_dashboard_layout_user_module
        UNIQUE (user_id, module_key)
);

CREATE INDEX IF NOT EXISTS idx_dashboard_layout_user_sort
    ON dashboard_layout (user_id, sort_index);

-- =========================
-- INBOX / DIRECT MESSAGING
-- =========================
CREATE TABLE IF NOT EXISTS conversation
(
    id         BIGSERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS conversation_participant
(
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    role_in_convo    VARCHAR(20) NOT NULL,
    last_read_at     TIMESTAMP   NULL,

    CONSTRAINT fk_convo_participant_convo
        FOREIGN KEY (conversation_id) REFERENCES conversation (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_convo_participant_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_convo_participant_unique
        UNIQUE (conversation_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_convo_participant_user
    ON conversation_participant (user_id);

CREATE TABLE IF NOT EXISTS message
(
    id                 BIGSERIAL PRIMARY KEY,
    conversation_id    BIGINT     NOT NULL,
    sender_user_id     BIGINT     NOT NULL,
    body               TEXT       NOT NULL,
    created_at         TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    linked_entity_type VARCHAR(80) NULL,
    linked_entity_id   BIGINT     NULL,

    CONSTRAINT fk_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversation (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_message_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_message_convo_created
    ON message (conversation_id, created_at);

-- =========================
-- NOTE FOLDERS + NOTES
-- =========================
CREATE TABLE IF NOT EXISTS note_folders
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    name       VARCHAR(80)  NOT NULL,
    colour     VARCHAR(20)  NOT NULL DEFAULT 'slate',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_note_folders_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notes
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    folder_id  BIGINT       NOT NULL,
    title      VARCHAR(120) NOT NULL,
    content    TEXT         NOT NULL,
    colour     VARCHAR(20)  NOT NULL DEFAULT 'slate',
    pinned     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_public  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_notes_folder
        FOREIGN KEY (folder_id) REFERENCES note_folders (id)
            ON DELETE CASCADE
);

-- =========================
-- TRAINING VAULT
-- =========================
-- =========================
-- USER POINTS / LEVELS
-- =========================
CREATE TABLE IF NOT EXISTS user_points
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL UNIQUE,
    points      INT    NOT NULL DEFAULT 0,
    level       INT    NOT NULL DEFAULT 1,
    last_updated DATE  NOT NULL DEFAULT CURRENT_DATE,

    CONSTRAINT fk_user_points_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- ROLES + USERS_ROLES
-- =========================
CREATE TABLE IF NOT EXISTS roles
(
    role_id SERIAL PRIMARY KEY,
    name    VARCHAR(45) NOT NULL
);

CREATE TABLE IF NOT EXISTS users_roles
(
    id       BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    role_id  INT          NOT NULL,

    CONSTRAINT fk_users_roles_role
        FOREIGN KEY (role_id) REFERENCES roles (role_id),

    CONSTRAINT fk_users_roles_username
        FOREIGN KEY (username) REFERENCES users (username)
            ON DELETE CASCADE
);

-- =========================
-- TRAINER <-> CLIENT LINKS
-- =========================
CREATE TABLE IF NOT EXISTS trainer_client_links
(
    id              BIGSERIAL PRIMARY KEY,
    client_id       BIGINT      NOT NULL,
    trainer_id      BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL,
    requested_at    TIMESTAMP   NULL,
    activated_at    TIMESTAMP   NULL,
    paused_at       TIMESTAMP   NULL,
    created_at      TIMESTAMP   NOT NULL,
    updated_at      TIMESTAMP   NOT NULL,
    ended_at        TIMESTAMP   NULL,
    coaching_phase  VARCHAR(30) NULL,
    coaching_phase_label VARCHAR(120) NULL,
    coaching_phase_started_at TIMESTAMP NULL,
    coaching_phase_updated_at TIMESTAMP NULL,

    CONSTRAINT fk_tcl_client
        FOREIGN KEY (client_id) REFERENCES users (id),

    CONSTRAINT fk_tcl_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_tcl_client_id
    ON trainer_client_links (client_id);

CREATE INDEX IF NOT EXISTS idx_tcl_trainer_id
    ON trainer_client_links (trainer_id);

CREATE INDEX IF NOT EXISTS idx_tcl_status
    ON trainer_client_links (status);

-- =========================
-- COACHING PHASE AUDIT
-- =========================
CREATE TABLE IF NOT EXISTS coaching_phase_changes
(
    id         BIGSERIAL PRIMARY KEY,
    link_id    BIGINT      NOT NULL,
    trainer_id BIGINT      NOT NULL,
    old_phase  VARCHAR(30) NULL,
    new_phase  VARCHAR(30) NOT NULL,
    old_label  VARCHAR(120) NULL,
    new_label  VARCHAR(120) NULL,
    changed_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes      VARCHAR(800) NULL,

    CONSTRAINT fk_coaching_phase_link
        FOREIGN KEY (link_id) REFERENCES trainer_client_links (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_coaching_phase_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- TRAINER SCHEDULE TEMPLATES
-- =========================
-- =========================
-- GOALS
-- =========================
CREATE TABLE IF NOT EXISTS goals
(
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    trainer_user_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    goal_type VARCHAR(30) NOT NULL,
    timeframe VARCHAR(20) NOT NULL DEFAULT 'TARGET',
    target_metric_name VARCHAR(120),
    target_metric_value DOUBLE PRECISION,
    target_metric_unit VARCHAR(30),
    start_date DATE,
    target_date DATE,
    status VARCHAR(20) NOT NULL,
    priority INTEGER,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_goal_trainer FOREIGN KEY (trainer_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_goals_owner
    ON goals (owner_user_id);

CREATE TABLE IF NOT EXISTS goal_links
(
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    link_type VARCHAR(30) NOT NULL,
    source VARCHAR(30) NOT NULL,
    calendar_task_id BIGINT,
    schedule_occurrence_id BIGINT,
    workout_session_id BIGINT,
    workout_template_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_links_goal FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_goal_links_goal
    ON goal_links (goal_id);

CREATE INDEX IF NOT EXISTS idx_goal_links_task
    ON goal_links (calendar_task_id);

CREATE INDEX IF NOT EXISTS idx_goal_links_occurrence
    ON goal_links (schedule_occurrence_id);

CREATE INDEX IF NOT EXISTS idx_goal_links_workout_session
    ON goal_links (workout_session_id);

CREATE TABLE IF NOT EXISTS goal_check_ins
(
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_by_role VARCHAR(30) NOT NULL,
    week_start_date DATE NOT NULL,
    reflection TEXT,
    confidence_rating INTEGER,
    trainer_comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_checkin_goal FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_checkin_user FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_goal_checkins_goal
    ON goal_check_ins (goal_id);

-- =========================
-- ACCOUNTABILITY STREAKS + SUMMARIES
-- =========================
CREATE TABLE IF NOT EXISTS user_streaks
(
    user_id            BIGINT      NOT NULL,
    streak_type        VARCHAR(20) NOT NULL,
    current_count      INT         NOT NULL DEFAULT 0,
    longest_count      INT         NOT NULL DEFAULT 0,
    last_completed_date DATE       NULL,
    updated_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_streaks PRIMARY KEY (user_id, streak_type),
    CONSTRAINT fk_user_streaks_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS weekly_summaries
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT      NOT NULL,
    week_start      DATE        NOT NULL,
    summary_json    TEXT        NULL,
    goals_json      TEXT        NULL,
    missed_items_json TEXT      NULL,
    streaks_json    TEXT        NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_weekly_summaries_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT uq_weekly_summaries_user_week
        UNIQUE (user_id, week_start)
);

-- =========================
-- WORKOUT BUILDER + PLAYER
-- =========================
CREATE TABLE IF NOT EXISTS workout_templates
(
    id            BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT       NOT NULL,
    owner_role    VARCHAR(30)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    description   VARCHAR(600) NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_workout_templates_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workout_templates_owner_updated
    ON workout_templates (owner_user_id, updated_at);

-- =========================
-- TRAINER ASSIGNMENTS
-- =========================
CREATE TABLE IF NOT EXISTS assigned_workouts
(
    id                  BIGSERIAL PRIMARY KEY,
    trainer_id          BIGINT       NOT NULL,
    client_id           BIGINT       NOT NULL,
    workout_template_id BIGINT       NOT NULL,
    trainer_notes       VARCHAR(800) NULL,
    client_notes        VARCHAR(1200) NULL,
    client_feedback     VARCHAR(1200) NULL,
    completed           BOOLEAN      NOT NULL DEFAULT FALSE,
    assigned_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP    NULL,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_assigned_workouts_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_assigned_workouts_client
        FOREIGN KEY (client_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_assigned_workouts_template
        FOREIGN KEY (workout_template_id) REFERENCES workout_templates (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assigned_workouts_client
    ON assigned_workouts (client_id, assigned_at);

CREATE TABLE IF NOT EXISTS schedules
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    schedule_type VARCHAR(20) NOT NULL DEFAULT 'WEEKLY',
    rotation_mode VARCHAR(30) NOT NULL DEFAULT 'WEEKLY_REPEAT',
    custom_day_count INT NOT NULL DEFAULT 7,
    template_id VARCHAR(100),

    CONSTRAINT fk_schedules_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS assigned_schedules
(
    id           BIGSERIAL PRIMARY KEY,
    trainer_id   BIGINT       NOT NULL,
    client_id    BIGINT       NOT NULL,
    schedule_id  BIGINT       NOT NULL,
    trainer_notes VARCHAR(800) NULL,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    assigned_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_assigned_schedules_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_assigned_schedules_client
        FOREIGN KEY (client_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_assigned_schedules_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_assigned_schedules_client
    ON assigned_schedules (client_id, assigned_at);

-- =========================
-- TRAINER <-> CLIENT MESSAGING (relationship scoped)
-- =========================
CREATE TABLE IF NOT EXISTS message_threads
(
    id         BIGSERIAL PRIMARY KEY,
    client_id  BIGINT      NOT NULL,
    trainer_id BIGINT      NOT NULL,
    link_id    BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL,

    CONSTRAINT fk_message_threads_client
        FOREIGN KEY (client_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_message_threads_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_message_threads_link
        FOREIGN KEY (link_id) REFERENCES trainer_client_links (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_message_threads_link
        UNIQUE (link_id)
);

CREATE INDEX IF NOT EXISTS idx_message_threads_trainer_status
    ON message_threads (trainer_id, status);

CREATE INDEX IF NOT EXISTS idx_message_threads_client_status
    ON message_threads (client_id, status);

CREATE TABLE IF NOT EXISTS message_thread_messages
(
    id             BIGSERIAL PRIMARY KEY,
    thread_id      BIGINT      NOT NULL,
    sender_user_id BIGINT      NOT NULL,
    type           VARCHAR(20) NOT NULL,
    body_text      TEXT        NOT NULL,
    attachment_name VARCHAR(200) NULL,
    attachment_url  VARCHAR(500) NULL,
    attachment_type VARCHAR(100) NULL,
    created_at     TIMESTAMP   NOT NULL,

    CONSTRAINT fk_mtm_thread
        FOREIGN KEY (thread_id) REFERENCES message_threads (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_mtm_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS message_read_states
(
    id         BIGSERIAL PRIMARY KEY,
    message_id BIGINT    NOT NULL,
    thread_id  BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    read_at    TIMESTAMP NOT NULL,

    CONSTRAINT fk_mrs_message
        FOREIGN KEY (message_id) REFERENCES message_thread_messages (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_mrs_thread
        FOREIGN KEY (thread_id) REFERENCES message_threads (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_mrs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_message_read_state
        UNIQUE (message_id, user_id)
);

CREATE TABLE IF NOT EXISTS off_platform_payment_attempts
(
    id             BIGSERIAL PRIMARY KEY,
    thread_id      BIGINT      NOT NULL,
    sender_user_id BIGINT      NOT NULL,
    matched_keyword VARCHAR(100) NULL,
    body_text      TEXT        NOT NULL,
    created_at     TIMESTAMP   NOT NULL,

    CONSTRAINT fk_off_platform_thread
        FOREIGN KEY (thread_id) REFERENCES message_threads (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_off_platform_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mtm_thread_created
    ON message_thread_messages (thread_id, created_at);

CREATE INDEX IF NOT EXISTS idx_mrs_thread_user
    ON message_read_states (thread_id, user_id, read_at);

-- =========================
-- TRAINER PROFILES
-- =========================
CREATE TABLE IF NOT EXISTS trainer_profiles
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT       NOT NULL UNIQUE,
    bio               VARCHAR(500) NULL,
        trainer_code      VARCHAR(12)  NULL UNIQUE,
    specializations   VARCHAR(200) NULL,
    location         VARCHAR(120) NULL,
    primary_gym      VARCHAR(200) NULL,
    price_per_session INTEGER NULL,
    
    -- Social Media URLs
    instagram_url     VARCHAR(500) NULL,
    tiktok_url        VARCHAR(500) NULL,
    youtube_url       VARCHAR(500) NULL,
    linkedin_url      VARCHAR(500) NULL,
    website_url       VARCHAR(500) NULL,
    
    -- Visibility Flags
    show_instagram    BOOLEAN      NOT NULL DEFAULT FALSE,
    show_tiktok       BOOLEAN      NOT NULL DEFAULT FALSE,
    show_youtube      BOOLEAN      NOT NULL DEFAULT FALSE,
    show_linkedin     BOOLEAN      NOT NULL DEFAULT FALSE,
    show_website      BOOLEAN      NOT NULL DEFAULT FALSE,
    
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,

    CONSTRAINT fk_trainer_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- GYM PROFILES
-- =========================
CREATE TABLE IF NOT EXISTS gym_profiles
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE,
    gym_name      VARCHAR(120) NOT NULL,
    gym_code      VARCHAR(16),
    address       VARCHAR(200),
    city          VARCHAR(120),
    contact_name  VARCHAR(120),
    contact_phone VARCHAR(40),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,

    CONSTRAINT fk_gym_profiles_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

ALTER TABLE gym_profiles
    ADD COLUMN IF NOT EXISTS gym_code VARCHAR(16);

ALTER TABLE gym_profiles
    ALTER COLUMN gym_code TYPE VARCHAR(16);

CREATE INDEX IF NOT EXISTS idx_trainer_profiles_user
    ON trainer_profiles (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_gym_profiles_gym_code
    ON gym_profiles (gym_code);

-- =========================
-- GYM MEMBERSHIP PRODUCTS
-- =========================
CREATE TABLE IF NOT EXISTS gym_membership_products
(
    id             BIGSERIAL PRIMARY KEY,
    gym_id         BIGINT       NOT NULL,
    name           VARCHAR(200) NOT NULL,
    description    TEXT         NULL,
    price_cents    INTEGER      NOT NULL,
    billing_period VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY',
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_gym_membership_products_gym
        FOREIGN KEY (gym_id) REFERENCES gym_profiles (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_gym_membership_products_gym_created
    ON gym_membership_products (gym_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_gym_membership_products_gym_active_created
    ON gym_membership_products (gym_id, active, created_at DESC);

-- =========================
-- GYM MEMBER SUBSCRIPTIONS
-- =========================
CREATE TABLE IF NOT EXISTS gym_member_subscriptions
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    gym_id       BIGINT       NOT NULL,
    product_id   BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    started_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    renews_at    TIMESTAMP    NOT NULL,
    cancelled_at TIMESTAMP    NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_gym_member_subscriptions_user_gym
        UNIQUE (user_id, gym_id),

    CONSTRAINT fk_gym_member_subscriptions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_gym_member_subscriptions_gym
        FOREIGN KEY (gym_id) REFERENCES gym_profiles (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_gym_member_subscriptions_product
        FOREIGN KEY (product_id) REFERENCES gym_membership_products (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_gym_member_subscriptions_product_status
    ON gym_member_subscriptions (product_id, status);

CREATE INDEX IF NOT EXISTS idx_gym_member_subscriptions_user_gym_status
    ON gym_member_subscriptions (user_id, gym_id, status);

CREATE INDEX IF NOT EXISTS idx_gym_member_subscriptions_gym_status
    ON gym_member_subscriptions (gym_id, status);

-- =========================
-- PRICE CHANGE EVENTS
-- =========================
CREATE TABLE IF NOT EXISTS price_change_events
(
    id                    BIGSERIAL PRIMARY KEY,
    gym_id                BIGINT       NOT NULL,
    product_id            BIGINT       NOT NULL,
    old_price_cents       INTEGER      NOT NULL,
    new_price_cents       INTEGER      NOT NULL,
    effective_at          TIMESTAMP    NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    changed_by_user_id    BIGINT       NOT NULL,
    affected_member_count INTEGER      NOT NULL DEFAULT 0,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_change_events_gym
        FOREIGN KEY (gym_id) REFERENCES gym_profiles (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_price_change_events_product
        FOREIGN KEY (product_id) REFERENCES gym_membership_products (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_price_change_events_changed_by
        FOREIGN KEY (changed_by_user_id) REFERENCES users (id)
            ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_price_change_events_product_created
    ON price_change_events (product_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_price_change_events_gym_created
    ON price_change_events (gym_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_price_change_events_product_effective
    ON price_change_events (product_id, effective_at DESC);

-- =========================
-- TRAINER VERIFICATION REQUESTS
-- =========================
CREATE TABLE IF NOT EXISTS trainer_verification_requests
(
    id                  BIGSERIAL PRIMARY KEY,
    trainer_user_id     BIGINT       NOT NULL,
    gym_id              BIGINT       NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    notes               TEXT         NULL,
    admin_notes         TEXT         NULL,
    submitted_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at         TIMESTAMP    NULL,
    reviewed_by_user_id BIGINT       NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_trainer_verification_requests_trainer
        FOREIGN KEY (trainer_user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_trainer_verification_requests_gym
        FOREIGN KEY (gym_id) REFERENCES gym_profiles (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_trainer_verification_requests_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_trainer_verification_requests_status_submitted
    ON trainer_verification_requests (status, submitted_at ASC);

CREATE INDEX IF NOT EXISTS idx_trainer_verification_requests_gym_submitted
    ON trainer_verification_requests (gym_id, submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_trainer_verification_requests_trainer_submitted
    ON trainer_verification_requests (trainer_user_id, submitted_at DESC);

-- =========================
-- TRAINER REVIEWS & CLIENT ASSESSMENTS
-- =========================
CREATE TABLE IF NOT EXISTS trainer_reviews
(
    id                BIGSERIAL PRIMARY KEY,
    trainer_user_id   BIGINT      NOT NULL,
    client_user_id    BIGINT      NOT NULL,
    link_id           BIGINT      NOT NULL,
    stars             INTEGER     NOT NULL CHECK (stars >= 1 AND stars <= 5),
    tags              VARCHAR(500),
    comment           TEXT,
    created_at        TIMESTAMP   NOT NULL,
    status            VARCHAR(20) NOT NULL,

    CONSTRAINT fk_tr_trainer
        FOREIGN KEY (trainer_user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tr_client
        FOREIGN KEY (client_user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tr_link
        FOREIGN KEY (link_id) REFERENCES trainer_client_links (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_trainer_reviews_link
        UNIQUE (trainer_user_id, client_user_id, link_id)
);

CREATE INDEX IF NOT EXISTS idx_trainer_reviews_trainer_status
    ON trainer_reviews (trainer_user_id, status);

CREATE INDEX IF NOT EXISTS idx_trainer_reviews_created
    ON trainer_reviews (created_at DESC);

CREATE TABLE IF NOT EXISTS client_assessments
(
    id                   BIGSERIAL PRIMARY KEY,
    trainer_user_id      BIGINT    NOT NULL,
    client_user_id       BIGINT    NOT NULL,
    reliability_score    INTEGER CHECK (reliability_score >= 1 AND reliability_score <= 5),
    communication_score  INTEGER CHECK (communication_score >= 1 AND communication_score <= 5),
    private_notes        TEXT,
    updated_at           TIMESTAMP NOT NULL,

    CONSTRAINT fk_ca_trainer
        FOREIGN KEY (trainer_user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_ca_client
        FOREIGN KEY (client_user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_client_assessments_trainer_client
        UNIQUE (trainer_user_id, client_user_id)
);

CREATE INDEX IF NOT EXISTS idx_client_assessments_trainer
    ON client_assessments (trainer_user_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS review_moderations
(
    id                   BIGSERIAL PRIMARY KEY,
    review_id            BIGINT       NOT NULL,
    reported_by_user_id  BIGINT       NOT NULL,
    reason               VARCHAR(1000),
    reported_at          TIMESTAMP    NOT NULL,
    resolved             BOOLEAN      NOT NULL DEFAULT FALSE,
    resolved_at          TIMESTAMP,
    resolved_by_user_id  BIGINT,
    resolution_notes     TEXT,

    CONSTRAINT fk_rm_review
        FOREIGN KEY (review_id) REFERENCES trainer_reviews (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_rm_reported_by
        FOREIGN KEY (reported_by_user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_rm_resolved_by
        FOREIGN KEY (resolved_by_user_id) REFERENCES users (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_review_moderations_resolved
    ON review_moderations (resolved, reported_at DESC);

CREATE INDEX IF NOT EXISTS idx_review_moderations_review
    ON review_moderations (review_id, reported_at DESC);

-- =========================
-- TRAINER LIBRARY (templates + structured coaching notes)
-- =========================
CREATE TABLE IF NOT EXISTS trainer_library_exercises
(
    id              BIGSERIAL PRIMARY KEY,
    trainer_id       BIGINT       NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     TEXT         NULL,
    primary_muscles VARCHAR(255) NOT NULL,
    equipment       VARCHAR(255) NOT NULL,
    difficulty      VARCHAR(30)  NOT NULL,
    video_url       VARCHAR(500) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tle_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tle_trainer
    ON trainer_library_exercises (trainer_id);

CREATE TABLE IF NOT EXISTS trainer_library_exercise_notes
(
    id          BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL,
    note_text   TEXT   NOT NULL,

    CONSTRAINT fk_tlen_exercise
        FOREIGN KEY (exercise_id) REFERENCES trainer_library_exercises (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tlen_exercise
    ON trainer_library_exercise_notes (exercise_id);

CREATE TABLE IF NOT EXISTS trainer_library_workout_templates
(
    id         BIGSERIAL PRIMARY KEY,
    trainer_id  BIGINT       NOT NULL,
    title      VARCHAR(120) NOT NULL,
    summary    TEXT         NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tlwt_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tlwt_trainer
    ON trainer_library_workout_templates (trainer_id);

CREATE TABLE IF NOT EXISTS trainer_library_workout_items
(
    id           BIGSERIAL PRIMARY KEY,
    workout_id   BIGINT NOT NULL,
    exercise_id  BIGINT NOT NULL,
    sets         INT    NOT NULL,
    reps         INT    NOT NULL,
    rest_seconds INT    NOT NULL,
    rpe          INT    NULL,
    order_index  INT    NOT NULL,

    CONSTRAINT fk_tlwi_workout
        FOREIGN KEY (workout_id) REFERENCES trainer_library_workout_templates (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tlwi_exercise
        FOREIGN KEY (exercise_id) REFERENCES trainer_library_exercises (id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_tlwi_workout_order
        UNIQUE (workout_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_tlwi_workout
    ON trainer_library_workout_items (workout_id);

CREATE TABLE IF NOT EXISTS trainer_library_workout_notes
(
    id        BIGSERIAL PRIMARY KEY,
    workout_id BIGINT NOT NULL,
    note_text TEXT   NOT NULL,

    CONSTRAINT fk_tlwn_workout
        FOREIGN KEY (workout_id) REFERENCES trainer_library_workout_templates (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tlwn_workout
    ON trainer_library_workout_notes (workout_id);

CREATE TABLE IF NOT EXISTS trainer_library_programme_templates
(
    id         BIGSERIAL PRIMARY KEY,
    trainer_id  BIGINT       NOT NULL,
    title      VARCHAR(120) NOT NULL,
    weeks      INT          NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tlpt_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tlpt_trainer
    ON trainer_library_programme_templates (trainer_id);

CREATE TABLE IF NOT EXISTS trainer_library_programme_days
(
    id           BIGSERIAL PRIMARY KEY,
    programme_id BIGINT      NOT NULL,
    day_of_week  VARCHAR(20) NOT NULL,
    workout_id   BIGINT      NOT NULL,
    order_index  INT         NOT NULL,

    CONSTRAINT fk_tlpd_programme
        FOREIGN KEY (programme_id) REFERENCES trainer_library_programme_templates (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tlpd_workout
        FOREIGN KEY (workout_id) REFERENCES trainer_library_workout_templates (id)
            ON DELETE RESTRICT,

    CONSTRAINT uq_tlpd_programme_order
        UNIQUE (programme_id, order_index)
);

CREATE INDEX IF NOT EXISTS idx_tlpd_programme
    ON trainer_library_programme_days (programme_id);

CREATE TABLE IF NOT EXISTS trainer_library_programme_notes
(
    id          BIGSERIAL PRIMARY KEY,
    programme_id BIGINT NOT NULL,
    note_text   TEXT   NOT NULL,

    CONSTRAINT fk_tlpn_programme
        FOREIGN KEY (programme_id) REFERENCES trainer_library_programme_templates (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tlpn_programme
    ON trainer_library_programme_notes (programme_id);

CREATE TABLE IF NOT EXISTS trainer_library_shared_templates
(
    id           BIGSERIAL PRIMARY KEY,
    trainer_id    BIGINT      NOT NULL,
    client_id    BIGINT      NOT NULL,
    template_type VARCHAR(20) NOT NULL,
    template_id  BIGINT      NOT NULL,
    shared_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tlst_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_tlst_client
        FOREIGN KEY (client_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_tlst_unique_share
        UNIQUE (trainer_id, client_id, template_type, template_id)
);

CREATE INDEX IF NOT EXISTS idx_tlst_client
    ON trainer_library_shared_templates (client_id, template_type);

CREATE INDEX IF NOT EXISTS idx_tlst_trainer
    ON trainer_library_shared_templates (trainer_id, template_type);

-- =========================
-- PHYSICAL CONDITIONS + HEALTH RECORDS
-- =========================
CREATE TABLE IF NOT EXISTS physical_conditions
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(180)
);

CREATE TABLE IF NOT EXISTS health_records
(
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT       NOT NULL,
    baseline_date            TIMESTAMP    NOT NULL,
    systolic_blood_pressure  INTEGER,
    diastolic_blood_pressure INTEGER,
    cholesterol              DOUBLE PRECISION,
    weight_kg                DOUBLE PRECISION NOT NULL,
    height_cm                DOUBLE PRECISION NOT NULL,
    bmi                      DOUBLE PRECISION,
    waist_height_ratio       DOUBLE PRECISION,
    waist_cm                 DOUBLE PRECISION NOT NULL,
    activity_level           VARCHAR(180),

    CONSTRAINT fk_health_records_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS record_condition
(
    health_record_id      BIGINT NOT NULL,
    physical_condition_id BIGINT NOT NULL,

    PRIMARY KEY (health_record_id, physical_condition_id),

    CONSTRAINT fk_record_condition_record
        FOREIGN KEY (health_record_id) REFERENCES health_records (id),

    CONSTRAINT fk_record_condition_condition
        FOREIGN KEY (physical_condition_id) REFERENCES physical_conditions (id)
);

-- =========================
-- EXERCISE LOG
-- =========================
CREATE TABLE IF NOT EXISTS exercise_log
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    date             DATE,
    mood_before      INT,
    mood_after       INT,
    confidence       INT,
    comments         VARCHAR(300),
    duration_minutes INT,
    occurrence_id    BIGINT NULL,
    calendar_task_id BIGINT NULL,

    CONSTRAINT fk_exercise_log_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE

    -- You had these commented out; keeping them commented to match behaviour
    -- ,CONSTRAINT fk_exercise_log_occurrence
    --     FOREIGN KEY (occurrence_id) REFERENCES schedule_occurrences (id) ON DELETE SET NULL
    -- ,CONSTRAINT fk_exercise_log_calendar_task
    --     FOREIGN KEY (calendar_task_id) REFERENCES calendar_tasks (id) ON DELETE SET NULL
);

-- =========================
-- EXERCISES + CUSTOM_EXERCISES
-- =========================
CREATE TABLE IF NOT EXISTS exercises
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    category    VARCHAR(100),
    description TEXT,
    video_url   VARCHAR(500),
    difficulty  INT,
    type        VARCHAR(50),
    image_url   VARCHAR(500)
);

CREATE TABLE IF NOT EXISTS custom_exercises
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(200) NOT NULL,
    category    VARCHAR(100),
    description TEXT,
    how_to      TEXT,
    video_url   VARCHAR(500),
    color_tag   VARCHAR(40),
    type        VARCHAR(50),
    image_url   VARCHAR(500),

    CONSTRAINT fk_custom_exercises_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- TRAINER SCHEDULE TEMPLATES
-- =========================
CREATE TABLE IF NOT EXISTS trainer_schedule_templates
(
    id          BIGSERIAL PRIMARY KEY,
    trainer_id  BIGINT      NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(800) NULL,
    tags        VARCHAR(500) NULL,
    version     INT         NOT NULL DEFAULT 1,
    archived    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_trainer_schedule_templates_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS trainer_schedule_template_entries
(
    id                 BIGSERIAL PRIMARY KEY,
    template_id        BIGINT      NOT NULL,
    day_of_week        INT         NOT NULL,
    time_window_start  TIME        NULL,
    time_window_end    TIME        NULL,
    type               VARCHAR(20) NOT NULL,
    title              VARCHAR(200) NOT NULL,
    defaults_json      TEXT        NULL,
    intensity_label    VARCHAR(80) NULL,
    intensity_level    INT         NULL,
    exercise_id        BIGINT      NULL,
    custom_exercise_id BIGINT      NULL,
    order_index        INT         NOT NULL DEFAULT 0,

    CONSTRAINT fk_trainer_schedule_entries_template
        FOREIGN KEY (template_id) REFERENCES trainer_schedule_templates (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_trainer_schedule_entries_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_trainer_schedule_entries_custom_exercise
        FOREIGN KEY (custom_exercise_id) REFERENCES custom_exercises (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_trainer_schedule_entries_template
    ON trainer_schedule_template_entries (template_id);

-- =========================
-- CHECK-IN QUESTIONS + WEEKLY CHECK-INS
-- =========================
CREATE TABLE IF NOT EXISTS trainer_checkin_questions
(
    id         BIGSERIAL PRIMARY KEY,
    template_id BIGINT     NOT NULL,
    prompt     VARCHAR(300) NOT NULL,
    order_index INT        NOT NULL DEFAULT 0,
    required   BOOLEAN     NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_trainer_checkin_template
        FOREIGN KEY (template_id) REFERENCES trainer_schedule_templates (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS weekly_check_ins
(
    id             BIGSERIAL PRIMARY KEY,
    trainer_id     BIGINT      NOT NULL,
    client_id      BIGINT      NOT NULL,
    template_id    BIGINT      NULL,
    week_start_date DATE       NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    responses_json TEXT        NULL,
    client_notes   TEXT        NULL,
    trainer_response TEXT      NULL,
    next_week_focus VARCHAR(600) NULL,
    goal_id        BIGINT      NULL,
    submitted_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at   TIMESTAMP   NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_weekly_checkin_trainer
        FOREIGN KEY (trainer_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_weekly_checkin_client
        FOREIGN KEY (client_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_weekly_checkin_template
        FOREIGN KEY (template_id) REFERENCES trainer_schedule_templates (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_weekly_checkin_goal
        FOREIGN KEY (goal_id) REFERENCES goals (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_weekly_checkins_trainer
    ON weekly_check_ins (trainer_id, submitted_at);

CREATE INDEX IF NOT EXISTS idx_weekly_checkins_client
    ON weekly_check_ins (client_id, submitted_at);

-- =========================
-- FAVOURITES
-- =========================
CREATE TABLE IF NOT EXISTS favourites
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT NOT NULL,
    exercise_id        BIGINT NULL,
    custom_exercise_id BIGINT NULL,

    CONSTRAINT chk_favourites_at_least_one_exercise
        CHECK ((exercise_id IS NOT NULL) OR (custom_exercise_id IS NOT NULL)),

    CONSTRAINT fk_favourites_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_favourites_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_favourites_custom_exercise
        FOREIGN KEY (custom_exercise_id) REFERENCES custom_exercises (id)
            ON DELETE CASCADE
);

-- =========================
-- TAGS + EXERCISES_TAGS
-- =========================
CREATE TABLE IF NOT EXISTS tags
(
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR(200) NOT NULL UNIQUE,
    category VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS exercises_tags
(
    id          BIGSERIAL PRIMARY KEY,
    exercise_id BIGINT NOT NULL,
    tag_id      BIGINT NOT NULL,

    CONSTRAINT fk_exercises_tags_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_exercises_tags_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id)
            ON DELETE CASCADE,

    CONSTRAINT unique_tagging UNIQUE (exercise_id, tag_id)
);


-- =========================
-- PREFERENCES
-- =========================
CREATE TABLE IF NOT EXISTS preferences
(
    id          BIGSERIAL PRIMARY KEY,
    category    VARCHAR(180),
    description TEXT
);

CREATE TABLE IF NOT EXISTS user_preferences
(
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,

    CONSTRAINT fk_user_preferences_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS user_preference_conditions
(
    user_preference_id    BIGINT NOT NULL,
    physical_condition_id BIGINT NOT NULL,

    PRIMARY KEY (user_preference_id, physical_condition_id),

    CONSTRAINT fk_user_pref_conditions_pref
        FOREIGN KEY (user_preference_id) REFERENCES user_preferences (id),

    CONSTRAINT fk_user_pref_conditions_condition
        FOREIGN KEY (physical_condition_id) REFERENCES physical_conditions (id)
);

CREATE TABLE IF NOT EXISTS selected_preferences
(
    user_preference_id BIGINT NOT NULL,
    preference_id      BIGINT NOT NULL,

    PRIMARY KEY (user_preference_id, preference_id),

    CONSTRAINT fk_selected_preferences_user_pref
        FOREIGN KEY (user_preference_id) REFERENCES user_preferences (id),

    CONSTRAINT fk_selected_preferences_preference
        FOREIGN KEY (preference_id) REFERENCES preferences (id)
);

CREATE TABLE IF NOT EXISTS preference_tag
(
    preference_id BIGINT NOT NULL,
    tag_id        BIGINT NOT NULL,

    PRIMARY KEY (preference_id, tag_id),

    CONSTRAINT fk_preference_tag_preference
        FOREIGN KEY (preference_id) REFERENCES preferences (id),

    CONSTRAINT fk_preference_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id)
);

CREATE TABLE IF NOT EXISTS physical_condition_tag
(
    physical_condition_id BIGINT NOT NULL,
    tag_id                BIGINT NOT NULL,

    PRIMARY KEY (physical_condition_id, tag_id),

    CONSTRAINT fk_physical_condition_tag_condition
        FOREIGN KEY (physical_condition_id) REFERENCES physical_conditions (id),

    CONSTRAINT fk_physical_condition_tag_tag
        FOREIGN KEY (tag_id) REFERENCES tags (id)
);

-- =========================
-- SCHEDULES
-- =========================
CREATE TABLE IF NOT EXISTS schedule_entries
(
    id                 BIGSERIAL PRIMARY KEY,
    schedule_id        BIGINT NOT NULL,
    exercise_id        BIGINT NULL,
    custom_exercise_id BIGINT NULL,

    day_of_week        INT    NOT NULL,
    order_number       INT    NOT NULL,

    CONSTRAINT fk_schedule_entries_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_schedule_entries_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_schedule_entries_custom_exercise
        FOREIGN KEY (custom_exercise_id) REFERENCES custom_exercises (id)
            ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS schedule_occurrences
(
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    exercise_id        BIGINT       NULL,
    custom_exercise_id BIGINT       NULL,
    schedule_id        BIGINT,
    date               DATE         NOT NULL,
    schedule_name      VARCHAR(200) NOT NULL,
    exercise_log_id    BIGINT       NULL,
    completed          BOOLEAN      DEFAULT FALSE,
    missed             BOOLEAN      NOT NULL DEFAULT FALSE,
    missed_at          TIMESTAMP    NULL,
    trainer_template_id BIGINT      NULL,
    trainer_template_entry_id BIGINT NULL,

    CONSTRAINT fk_schedule_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_schedule_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_schedule_custom_exercise
        FOREIGN KEY (custom_exercise_id) REFERENCES custom_exercises (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_schedule_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_schedule_occurrence_log
        FOREIGN KEY (exercise_log_id) REFERENCES exercise_log (id)
            ON DELETE SET NULL
);

-- =========================
-- CALENDAR TASKS
-- =========================
CREATE TABLE IF NOT EXISTS calendar_tasks
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    date            DATE         NOT NULL,
    title           VARCHAR(200) NOT NULL,
    time            TIME         NULL,
    notes           TEXT         NULL,
    is_exercise     BOOLEAN      NOT NULL DEFAULT FALSE,
    completed       BOOLEAN      NOT NULL DEFAULT FALSE,
    missed          BOOLEAN      NOT NULL DEFAULT FALSE,
    missed_at       TIMESTAMP    NULL,
    grace_period_minutes INT     NULL,
    exercise_log_id BIGINT       NULL,
    exercise_name   VARCHAR(200) NULL,
    requires_log    BOOLEAN      NOT NULL DEFAULT FALSE,
    trainer_template_id BIGINT   NULL,
    trainer_template_entry_id BIGINT NULL,
    activity_type       VARCHAR(20)  NULL,

    CONSTRAINT fk_calendar_tasks_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- TASK WARNINGS
-- =========================
CREATE TABLE IF NOT EXISTS calendar_task_warnings
(
    id               BIGSERIAL PRIMARY KEY,
    calendar_task_id BIGINT      NOT NULL,
    trigger_type     VARCHAR(20) NOT NULL,
    trigger_time     TIME        NULL,
    trigger_task_id  BIGINT      NULL,
    triggered_at     TIMESTAMP   NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_calendar_task_warnings_task
        FOREIGN KEY (calendar_task_id) REFERENCES calendar_tasks (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_calendar_task_warnings_trigger_task
        FOREIGN KEY (trigger_task_id) REFERENCES calendar_tasks (id)
            ON DELETE SET NULL,

    CONSTRAINT chk_calendar_task_warnings_trigger_type
        CHECK (trigger_type IN ('TIME', 'ON_TASK_COMPLETE'))
);

CREATE INDEX IF NOT EXISTS idx_calendar_task_warnings_task
    ON calendar_task_warnings (calendar_task_id);

CREATE INDEX IF NOT EXISTS idx_calendar_task_warnings_trigger_task
    ON calendar_task_warnings (trigger_task_id);

-- =========================
-- TASK TEMPLATES (PERSONAL)
-- =========================
CREATE TABLE IF NOT EXISTS task_templates
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    notes        TEXT         NULL,
    is_exercise  BOOLEAN      NOT NULL DEFAULT FALSE,
    is_favourite BOOLEAN      NOT NULL DEFAULT FALSE,
    last_used_at TIMESTAMP    NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activity_type VARCHAR(20) NULL,

    CONSTRAINT fk_task_templates_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_templates_user_last_used
    ON task_templates (user_id, last_used_at);

CREATE INDEX IF NOT EXISTS idx_task_templates_user_favourite
    ON task_templates (user_id, is_favourite);

-- =========================
-- DAY COMPLETION (DAILY STREAK)
-- =========================
CREATE TABLE IF NOT EXISTS daily_completion
(
    user_id               BIGINT      NOT NULL,
    date                  DATE        NOT NULL,
    completion_status     VARCHAR(10) NOT NULL DEFAULT 'GREY',
    completion_percentage INT         NOT NULL DEFAULT 0,
    updated_at            TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, date),

    CONSTRAINT fk_daily_completion_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT chk_daily_completion_status
        CHECK (completion_status IN ('GREY', 'ORANGE', 'GREEN', 'RED')),

    CONSTRAINT chk_daily_completion_percentage
        CHECK (completion_percentage >= 0 AND completion_percentage <= 100)
);

-- =========================
-- DAILY FOCUS (PER DAY)
-- =========================
CREATE TABLE IF NOT EXISTS daily_focus
(
    user_id    BIGINT        NOT NULL,
    date       DATE          NOT NULL,
    daily_focus VARCHAR(120) NOT NULL,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, date),

    CONSTRAINT fk_daily_focus_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- ADAPTIVE FEEDBACK (PER DAY)
-- =========================
CREATE TABLE IF NOT EXISTS adaptive_feedback
(
    user_id       BIGINT        NOT NULL,
    date          DATE          NOT NULL,
    feedback_text VARCHAR(1000) NOT NULL,
    tone          VARCHAR(32)   NOT NULL,
    feedback_hash VARCHAR(64)   NOT NULL,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, date),

    CONSTRAINT fk_adaptive_feedback_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- DAY HEALTH (PER DAY)
-- =========================
CREATE TABLE IF NOT EXISTS day_health
(
    user_id         BIGINT    NOT NULL,
    date            DATE      NOT NULL,
    primary_message TEXT      NOT NULL,
    suggestion_a    TEXT      NOT NULL,
    suggestion_b    TEXT      NOT NULL,
    watch_out       TEXT      NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (user_id, date),

    CONSTRAINT fk_day_health_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- DAILY NUTRITION LOGS
-- =========================
CREATE TABLE IF NOT EXISTS daily_nutrition_logs
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    log_date      DATE        NOT NULL,
    calories      INTEGER     NOT NULL,
    protein_grams INTEGER     NOT NULL,
    carbs_grams   INTEGER     NOT NULL,
    fat_grams     INTEGER     NOT NULL,
    fibre_grams   INTEGER     NULL,
    water_ml      INTEGER     NULL,
    notes         VARCHAR(1000) NULL,
    updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_daily_nutrition_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_daily_nutrition_user_date
        UNIQUE (user_id, log_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_nutrition_user
    ON daily_nutrition_logs (user_id);

CREATE INDEX IF NOT EXISTS idx_daily_nutrition_user_date
    ON daily_nutrition_logs (user_id, log_date);

-- =========================
-- WORKOUTS
-- =========================
CREATE TABLE IF NOT EXISTS workouts
(
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name    VARCHAR(200),
    notes   TEXT,

    CONSTRAINT fk_workouts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS workouts_exercises
(
    workout_id  BIGINT NOT NULL,
    exercise_id BIGINT NOT NULL,

    PRIMARY KEY (workout_id, exercise_id),

    CONSTRAINT fk_workouts_exercises_workout
        FOREIGN KEY (workout_id) REFERENCES workouts (id),

    CONSTRAINT fk_workouts_exercises_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (id)
);

CREATE TABLE IF NOT EXISTS workouts_custom_exercises
(
    workout_id         BIGINT NOT NULL,
    custom_exercise_id BIGINT NOT NULL,

    PRIMARY KEY (workout_id, custom_exercise_id),

    CONSTRAINT fk_workouts_custom_exercises_workout
        FOREIGN KEY (workout_id) REFERENCES workouts (id),

    CONSTRAINT fk_workouts_custom_exercises_custom
        FOREIGN KEY (custom_exercise_id) REFERENCES custom_exercises (id)
);

CREATE TABLE IF NOT EXISTS workout_template_exercises
(
    id                 BIGSERIAL PRIMARY KEY,
    template_id        BIGINT       NOT NULL,
    exercise_id        BIGINT       NULL,
    custom_exercise_id BIGINT       NULL,
    exercise_name      VARCHAR(200) NOT NULL,
    sets               INT          NOT NULL DEFAULT 3,
    reps               INT          NOT NULL DEFAULT 10,
    rest_seconds       INT          NOT NULL DEFAULT 60,
    notes              VARCHAR(500) NULL,
    order_index        INT          NOT NULL DEFAULT 0,

    CONSTRAINT fk_wte_template
        FOREIGN KEY (template_id) REFERENCES workout_templates (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_wte_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_wte_custom_exercise
        FOREIGN KEY (custom_exercise_id) REFERENCES custom_exercises (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_wte_template_order
    ON workout_template_exercises (template_id, order_index);

CREATE TABLE IF NOT EXISTS workout_player_sessions
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    template_id   BIGINT       NOT NULL,
    name_snapshot VARCHAR(200) NULL,
    completed     BOOLEAN      NOT NULL DEFAULT FALSE,
    total_volume  DOUBLE PRECISION NULL,
    started_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at  TIMESTAMP    NULL,

    CONSTRAINT fk_workout_player_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_workout_player_sessions_template
        FOREIGN KEY (template_id) REFERENCES workout_templates (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workout_player_sessions_user_started
    ON workout_player_sessions (user_id, started_at);

CREATE TABLE IF NOT EXISTS workout_set_logs
(
    id             BIGSERIAL PRIMARY KEY,
    session_id     BIGINT       NOT NULL,
    exercise_name  VARCHAR(200) NOT NULL,
    exercise_order INT          NOT NULL DEFAULT 0,
    set_number     INT          NOT NULL DEFAULT 1,
    target_reps    INT          NULL,
    rest_seconds   INT          NULL,
    weight         DOUBLE PRECISION NULL,
    reps           INT          NULL,
    notes          VARCHAR(500) NULL,
    completed      BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_workout_set_logs_session
        FOREIGN KEY (session_id) REFERENCES workout_player_sessions (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workout_set_logs_session
    ON workout_set_logs (session_id);

CREATE TABLE IF NOT EXISTS workout_set_videos
(
    id          BIGSERIAL PRIMARY KEY,
    set_log_id  BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    path        VARCHAR(500) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_workout_set_videos_set
        FOREIGN KEY (set_log_id) REFERENCES workout_set_logs (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workout_set_videos_set
    ON workout_set_videos (set_log_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ai_form_feedback
(
    id         BIGSERIAL PRIMARY KEY,
    video_id   BIGINT       NOT NULL,
    rep_count  INT          NULL,
    tempo      VARCHAR(40)  NULL,
    flags_json TEXT         NULL,
    confidence DOUBLE PRECISION NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ai_form_feedback_video
        FOREIGN KEY (video_id) REFERENCES workout_set_videos (id)
            ON DELETE CASCADE,

    CONSTRAINT uq_ai_form_feedback_video
        UNIQUE (video_id)
);

-- =========================
-- STRENGTH LOG (WORKOUT SESSIONS)
-- =========================
CREATE TABLE IF NOT EXISTS workout_schedule
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    day_of_week INT    NOT NULL,
    workout_id  BIGINT NOT NULL,
    order_index INT    NOT NULL DEFAULT 0,

    CONSTRAINT fk_workout_schedule_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_workout_schedule_workout
        FOREIGN KEY (workout_id) REFERENCES workouts (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workout_schedule_user_dow
    ON workout_schedule (user_id, day_of_week, order_index);

CREATE TABLE IF NOT EXISTS workout_sessions
(
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NOT NULL,
    date                 DATE         NOT NULL,
    workout_id           BIGINT       NULL,
    schedule_id          BIGINT       NULL,
    source_occurrence_id BIGINT       NULL,
    name_snapshot        VARCHAR(200) NULL,
    completed            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_workout_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_workout_sessions_workout
        FOREIGN KEY (workout_id) REFERENCES workouts (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_workout_sessions_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_workout_sessions_user_date
    ON workout_sessions (user_id, date);

-- =========================
-- TRAINING VAULT
-- =========================
CREATE TABLE IF NOT EXISTS vault_notes
(
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT       NOT NULL,
    note_type                 VARCHAR(20)  NOT NULL,
    title                     VARCHAR(120) NOT NULL,
    content                   TEXT         NOT NULL,
    linked_date               DATE         NULL,
    linked_workout_session_id BIGINT       NULL,
    trainer_template_id        BIGINT       NULL,
    trainer_template_entry_id  BIGINT       NULL,
    pinned                    BOOLEAN      NOT NULL DEFAULT FALSE,
    tags                      VARCHAR(255) NOT NULL DEFAULT '',
    mood                      VARCHAR(20)  NULL,
    ai_summary                TEXT         NULL,
    ai_generated_at           TIMESTAMP    NULL,
    ai_source_revision        VARCHAR(64)  NULL,
    created_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vault_notes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_vault_notes_workout_session
        FOREIGN KEY (linked_workout_session_id) REFERENCES workout_sessions (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_vault_notes_user_updated
    ON vault_notes (user_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_vault_notes_user_type
    ON vault_notes (user_id, note_type);

CREATE TABLE IF NOT EXISTS exercise_sessions
(
    id                 BIGSERIAL PRIMARY KEY,
    workout_session_id BIGINT       NOT NULL,
    exercise_id        BIGINT       NULL,
    custom_exercise_id BIGINT       NULL,
    order_index        INT          NOT NULL DEFAULT 0,
    mode               VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    group_key          VARCHAR(100),
    completed          BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_exercise_sessions_workout_session
        FOREIGN KEY (workout_session_id) REFERENCES workout_sessions (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_exercise_sessions_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercises (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_exercise_sessions_custom_exercise
        FOREIGN KEY (custom_exercise_id) REFERENCES custom_exercises (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_exercise_sessions_workout_session
    ON exercise_sessions (workout_session_id);

CREATE TABLE IF NOT EXISTS set_logs
(
    id                  BIGSERIAL PRIMARY KEY,
    exercise_session_id  BIGINT  NOT NULL,
    set_number           INT     NULL,
    weight               DOUBLE PRECISION  NULL,
    reps                 INT     NULL,
        set_type             VARCHAR(20) NULL,
    notes                VARCHAR(500) NULL,
    completed            BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_set_logs_exercise_session
        FOREIGN KEY (exercise_session_id) REFERENCES exercise_sessions (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_set_logs_exercise_session
    ON set_logs (exercise_session_id);

-- =========================
-- SCHEDULE APPLIED
-- =========================
CREATE TABLE IF NOT EXISTS schedule_applied
(
    id               BIGSERIAL PRIMARY KEY,
    schedule_id      BIGINT  NOT NULL,
    user_id          BIGINT  NOT NULL,
    date_applied     DATE    NOT NULL,
    shown_on_calendar BOOLEAN NOT NULL DEFAULT TRUE,
    requires_logging  BOOLEAN NOT NULL DEFAULT FALSE,
    duration_weeks    INT     NOT NULL DEFAULT 4,

    CONSTRAINT fk_schedule_applied_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedules (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_schedule_applied_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- CHAT MESSAGES
-- =========================
CREATE TABLE IF NOT EXISTS chat_folders
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(120) NOT NULL,
    color_hex  VARCHAR(10) NOT NULL,
    icon_key   VARCHAR(40) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,

    CONSTRAINT fk_chat_folders_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_threads
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    chat_type  VARCHAR(32)  NOT NULL DEFAULT 'AI_PERSONAL',
    peer_user_id BIGINT      NULL,
    folder_id  BIGINT       NULL,
    title      VARCHAR(160) NOT NULL,
    color_hex  VARCHAR(10)  NOT NULL,
    icon_key   VARCHAR(40)  NOT NULL,
    pinned     BOOLEAN      NOT NULL DEFAULT FALSE,
    archived   BOOLEAN      NOT NULL DEFAULT FALSE,
    custom_instructions TEXT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,

    CONSTRAINT fk_chat_threads_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_chat_threads_folder
        FOREIGN KEY (folder_id) REFERENCES chat_folders (id)
            ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS chat_messages
(
    id         BIGSERIAL PRIMARY KEY,
    thread_id  BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL,

    CONSTRAINT fk_chat_messages_thread
        FOREIGN KEY (thread_id) REFERENCES chat_threads (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_folders_user
    ON chat_folders (user_id);

CREATE INDEX IF NOT EXISTS idx_chat_threads_user
    ON chat_threads (user_id);

CREATE INDEX IF NOT EXISTS idx_chat_threads_folder
    ON chat_threads (folder_id);

CREATE INDEX IF NOT EXISTS idx_chat_messages_thread
    ON chat_messages (thread_id);
-- =========================
-- COACH BOT CONVERSATIONS
-- =========================
CREATE TABLE IF NOT EXISTS coach_conversations
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_coach_conversations_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_coach_conversations_user
    ON coach_conversations (user_id);

CREATE INDEX IF NOT EXISTS idx_coach_conversations_user_updated
    ON coach_conversations (user_id, updated_at);

-- =========================
-- COACH BOT MESSAGES
-- =========================
CREATE TABLE IF NOT EXISTS coach_messages
(
    id               BIGSERIAL PRIMARY KEY,
    conversation_id  BIGINT      NOT NULL,
    role             VARCHAR(12) NOT NULL,
    content          TEXT        NOT NULL,
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_coach_messages_conversation
        FOREIGN KEY (conversation_id) REFERENCES coach_conversations (id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_coach_messages_conversation
    ON coach_messages (conversation_id);

CREATE INDEX IF NOT EXISTS idx_coach_messages_conversation_created
    ON coach_messages (conversation_id, created_at);

-- =========================
-- COACH BOT ACTION LOGS
-- =========================
CREATE TABLE IF NOT EXISTS coach_action_logs
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT     NOT NULL,
    conversation_id BIGINT     NULL,
    action_type     VARCHAR(40) NOT NULL,
    payload_json    TEXT       NOT NULL,
    success         BOOLEAN    NOT NULL,
    error_message   TEXT       NULL,
    created_at      TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_coach_action_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_coach_action_logs_conversation
        FOREIGN KEY (conversation_id) REFERENCES coach_conversations (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_coach_action_logs_user
    ON coach_action_logs (user_id, created_at);

-- =========================
-- DAILY USAGE LIMITS
-- =========================
CREATE TABLE IF NOT EXISTS daily_usage
(
    user_id     BIGINT  NOT NULL,
    usage_date  DATE    NOT NULL,
    used_count  INT     NOT NULL DEFAULT 0,

    CONSTRAINT pk_daily_usage PRIMARY KEY (user_id, usage_date),
    CONSTRAINT fk_daily_usage_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
);

-- =========================
-- BLOOD PRESSURE READINGS
-- =========================
CREATE TABLE IF NOT EXISTS blood_pressure_readings
(
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    reading_date DATE         NOT NULL,
    reading_time TIME         NULL,
    systolic     INT          NOT NULL,
    diastolic    INT          NOT NULL,
    pulse        INT          NULL,
    arm          VARCHAR(10)  NULL,
    position     VARCHAR(10)  NULL,
    notes        VARCHAR(500) NULL,
    source       VARCHAR(10)  NOT NULL DEFAULT 'MANUAL',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- =========================
-- DAY OPTIMISATIONS
-- =========================
CREATE TABLE IF NOT EXISTS day_optimisations
(
    user_id    BIGINT      NOT NULL,
    date       DATE        NOT NULL,
    day_theme  VARCHAR(30) NOT NULL DEFAULT 'CLEAN',
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, date),
    CONSTRAINT fk_day_optimisations_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_day_optimisations_user_date
    ON day_optimisations (user_id, date);

-- =========================
-- WORKOUT TEMPLATE SYSTEM
-- =========================
CREATE TABLE IF NOT EXISTS workout_ui_templates
(
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NULL,
    name        VARCHAR(200) NOT NULL,
    layout_type VARCHAR(30)  NOT NULL DEFAULT 'FLOW',
    config_json TEXT         NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workout_ui_templates_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_workout_ui_templates_user
    ON workout_ui_templates (user_id);

CREATE TABLE IF NOT EXISTS workout_template_sessions
(
    id                           BIGSERIAL     PRIMARY KEY,
    user_id                      BIGINT        NOT NULL,
    workout_id                   BIGINT        NOT NULL,
    template_id                  BIGINT        NULL,
    template_name_snapshot       VARCHAR(200)  NULL,
    config_json_snapshot         TEXT          NULL,
    started_at                   TIMESTAMP     NULL,
    ended_at                     TIMESTAMP     NULL,
    mood_before                  INT           NULL,
    mood_after                   INT           NULL,
    confidence                   INT           NULL,
    total_volume                 DECIMAL(10,2) NULL,
    status                       VARCHAR(20)   NOT NULL DEFAULT 'IN_PROGRESS',
    allow_completed_without_log  BOOLEAN       NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_workout_template_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_workout_template_sessions_workout
        FOREIGN KEY (workout_id) REFERENCES workouts (id),
    CONSTRAINT fk_workout_template_sessions_template
        FOREIGN KEY (template_id) REFERENCES workout_ui_templates (id)
);

CREATE INDEX IF NOT EXISTS idx_workout_template_sessions_user
    ON workout_template_sessions (user_id, started_at);

CREATE TABLE IF NOT EXISTS workout_session_exercises
(
    id                 BIGSERIAL    PRIMARY KEY,
    session_id         BIGINT       NOT NULL,
    exercise_id        BIGINT       NULL,
    custom_exercise_id BIGINT       NULL,
    order_index        INT          NOT NULL DEFAULT 0,
    mode               VARCHAR(20)  NOT NULL DEFAULT 'NORMAL',
    group_key          VARCHAR(100) NULL,
    notes              VARCHAR(1000) NULL,
    CONSTRAINT fk_workout_session_exercises_session
        FOREIGN KEY (session_id) REFERENCES workout_template_sessions (id)
);

CREATE INDEX IF NOT EXISTS idx_workout_session_exercises_session
    ON workout_session_exercises (session_id, order_index);

CREATE TABLE IF NOT EXISTS workout_session_sets
(
    id                  BIGSERIAL    PRIMARY KEY,
    session_exercise_id BIGINT       NOT NULL,
    set_index           INT          NOT NULL DEFAULT 0,
    reps                INT          NULL,
    weight              DECIMAL(8,2) NULL,
    rpe                 DECIMAL(4,1) NULL,
    tempo               VARCHAR(20)  NULL,
    is_drop             BOOLEAN      NOT NULL DEFAULT FALSE,
    completed_at        TIMESTAMP    NULL,
    CONSTRAINT fk_workout_session_sets_exercise
        FOREIGN KEY (session_exercise_id) REFERENCES workout_session_exercises (id)
);

CREATE INDEX IF NOT EXISTS idx_workout_session_sets_exercise
    ON workout_session_sets (session_exercise_id, set_index);

-- =========================
-- WAITLIST EMAILS
-- =========================
CREATE TABLE IF NOT EXISTS waitlist_emails
(
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    email        VARCHAR(255) NOT NULL UNIQUE,
    signed_up_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed    BOOLEAN      NOT NULL DEFAULT FALSE
);

-- =========================
-- SUPPORT FEEDBACK REQUESTS
-- =========================
CREATE TABLE IF NOT EXISTS support_requests
(
    id                   BIGSERIAL PRIMARY KEY,
    user_id              BIGINT       NULL,
    request_type         VARCHAR(30)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'NEW',
    submitter_name       VARCHAR(120) NULL,
    submitter_email      VARCHAR(255) NULL,
    subject              VARCHAR(180) NOT NULL,
    message              VARCHAR(5000) NOT NULL,
    allow_email_reply    BOOLEAN      NOT NULL DEFAULT FALSE,
    viewed               BOOLEAN      NOT NULL DEFAULT FALSE,
    admin_response       VARCHAR(5000) NULL,
    responded_by_user_id BIGINT       NULL,
    responded_at         TIMESTAMP    NULL,
    submitted_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_support_requests_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_support_requests_responder
        FOREIGN KEY (responded_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_support_requests_submitted_at
    ON support_requests (submitted_at DESC);

CREATE INDEX IF NOT EXISTS idx_support_requests_status
    ON support_requests (status, viewed);

-- =========================
-- GYM APPLICATIONS
-- =========================
CREATE TABLE IF NOT EXISTS gym_applications
(
    id                    BIGSERIAL PRIMARY KEY,
    gym_name              VARCHAR(120) NOT NULL,
    admin_email           VARCHAR(100) NOT NULL,
    gym_username          VARCHAR(100) NOT NULL,
    requested_password_hash VARCHAR(500) NOT NULL,
    address               VARCHAR(200) NOT NULL,
    city                  VARCHAR(120) NOT NULL,
    contact_name          VARCHAR(120) NOT NULL,
    contact_phone         VARCHAR(40)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    access_token          VARCHAR(120) NOT NULL,
    review_notes          TEXT         NULL,
    reviewed_at           TIMESTAMP    NULL,
    reviewed_by_user_id   BIGINT       NULL,
    approved_user_id      BIGINT       NULL,
    submitted_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_gym_applications_access_token UNIQUE (access_token),
    CONSTRAINT fk_gym_applications_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id)
            ON DELETE SET NULL,
    CONSTRAINT fk_gym_applications_approved_user
        FOREIGN KEY (approved_user_id) REFERENCES users (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gym_applications_status
    ON gym_applications (status, submitted_at);

CREATE TABLE IF NOT EXISTS gym_application_messages
(
    id             BIGSERIAL PRIMARY KEY,
    application_id BIGINT       NOT NULL,
    sender_type    VARCHAR(20)  NOT NULL,
    sender_user_id BIGINT       NULL,
    sender_email   VARCHAR(255) NULL,
    subject        VARCHAR(200) NULL,
    body           TEXT         NOT NULL,
    emailed        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_gym_application_messages_application
        FOREIGN KEY (application_id) REFERENCES gym_applications (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_gym_application_messages_sender
        FOREIGN KEY (sender_user_id) REFERENCES users (id)
            ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_gym_application_messages_application
    ON gym_application_messages (application_id, created_at);

-- =========================
-- MERCH PRODUCTS
-- =========================
CREATE TABLE IF NOT EXISTS merch_products
(
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(200)   NOT NULL,
    description         TEXT           NULL,
    price               NUMERIC(10, 2) NOT NULL,
    image_url           VARCHAR(500)   NULL,
    secondary_image_url VARCHAR(500)   NULL,
    stock_quantity      INT            NOT NULL DEFAULT 0,
    category       VARCHAR(100)   NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT         NULL,
    CONSTRAINT fk_merch_product_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_merch_products_active ON merch_products (active);

-- =========================
-- SAVED PAYMENT METHODS
-- =========================
CREATE TABLE IF NOT EXISTS saved_payment_methods
(
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT       NOT NULL,
    card_holder_name            VARCHAR(200) NOT NULL,
    last_four                   VARCHAR(4)   NOT NULL,
    brand                       VARCHAR(50)  NOT NULL,
    expiry_month                SMALLINT     NOT NULL,
    expiry_year                 SMALLINT     NOT NULL,
    provider_payment_method_id  TEXT         NOT NULL DEFAULT '',
    is_default                  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_method_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_payment_methods_user ON saved_payment_methods (user_id);

-- =========================
-- MERCH ORDERS
-- =========================
CREATE TABLE IF NOT EXISTS merch_orders
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT         NOT NULL,
    checkout_key      VARCHAR(36)    NULL,
    checkout_currency VARCHAR(3)     NULL,
    checkout_success_url VARCHAR(2048) NULL,
    checkout_cancel_url VARCHAR(2048)  NULL,
    status            VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    payment_status    VARCHAR(30)    NOT NULL DEFAULT 'PENDING_PAYMENT',
    payment_provider  VARCHAR(50)    NULL,
    payment_reference VARCHAR(200)   NULL,
    payment_failure_reason VARCHAR(500) NULL,
    payment_confirmed_at TIMESTAMP   NULL,
    total_amount      NUMERIC(10, 2) NOT NULL,
    payment_method_id BIGINT         NULL,
    refund_status     VARCHAR(20)    NOT NULL DEFAULT 'NONE',
    refund_reference  VARCHAR(200)   NULL,
    shipping_status   VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    tracking_number   VARCHAR(200)   NULL,
    shipped_at        TIMESTAMP      NULL,
    delivered_at      TIMESTAMP      NULL,
    estimated_delivery_date DATE     NULL,
    created_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_merch_order_user   FOREIGN KEY (user_id)           REFERENCES users                  (id) ON DELETE CASCADE,
    CONSTRAINT fk_merch_order_pm     FOREIGN KEY (payment_method_id) REFERENCES saved_payment_methods  (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_merch_orders_user ON merch_orders (user_id, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_merch_order_user_checkout ON merch_orders (user_id, checkout_key);

-- =========================
-- MERCH ORDER ITEMS
-- =========================
CREATE TABLE IF NOT EXISTS merch_order_items
(
    id                    BIGSERIAL PRIMARY KEY,
    order_id              BIGINT         NOT NULL,
    product_id            BIGINT         NULL,
    product_name_snapshot VARCHAR(200)   NOT NULL,
    price_snapshot        NUMERIC(10, 2) NOT NULL,
    quantity              INT            NOT NULL DEFAULT 1,
    image_url_snapshot    VARCHAR(500)   NULL,
    category_snapshot     VARCHAR(100)   NULL,
    CONSTRAINT fk_order_item_order   FOREIGN KEY (order_id)   REFERENCES merch_orders   (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES merch_products (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_order_items_order ON merch_order_items (order_id);

-- =========================
-- VIEW: USER_AUTHORITIES
-- =========================
CREATE VIEW user_authorities AS
SELECT
    u.username AS username,
    ('ROLE_' || r.name) AS authority
FROM users u
         JOIN users_roles ur ON u.username = ur.username
         JOIN roles r ON ur.role_id = r.role_id;

-- =========================
-- INDEXES
-- =========================
CREATE INDEX IF NOT EXISTS idx_fav_user
    ON favourites (user_id);

CREATE INDEX IF NOT EXISTS idx_custom_user
    ON custom_exercises (user_id);

CREATE INDEX IF NOT EXISTS idx_users_username
    ON users (username);

CREATE INDEX IF NOT EXISTS idx_occ_user_date
    ON schedule_occurrences (user_id, date);

CREATE INDEX IF NOT EXISTS idx_entry_schedule
    ON schedule_entries (schedule_id);

CREATE INDEX IF NOT EXISTS idx_schedule_user
    ON schedules (user_id);

-- =========================
-- OPERATIONAL READINESS
-- =========================
CREATE TABLE IF NOT EXISTS spring_session
(
    primary_id            CHAR(36)     NOT NULL PRIMARY KEY,
    session_id            CHAR(36)     NOT NULL,
    creation_time         BIGINT       NOT NULL,
    last_access_time      BIGINT       NOT NULL,
    max_inactive_interval INT          NOT NULL,
    expiry_time           BIGINT       NOT NULL,
    principal_name        VARCHAR(100)
);

CREATE UNIQUE INDEX IF NOT EXISTS spring_session_ix1 ON spring_session (session_id);
CREATE INDEX IF NOT EXISTS spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX IF NOT EXISTS spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE IF NOT EXISTS spring_session_attributes
(
    session_primary_id CHAR(36)     NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BINARY LARGE OBJECT NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk
        FOREIGN KEY (session_primary_id) REFERENCES spring_session (primary_id)
            ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS login_attempts
(
    attempt_key_hash CHAR(64)    PRIMARY KEY,
    attempt_count    INT         NOT NULL,
    window_start     TIMESTAMP WITH TIME ZONE NULL,
    blocked_until    TIMESTAMP WITH TIME ZONE NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_login_attempts_updated_at ON login_attempts (updated_at);

CREATE TABLE IF NOT EXISTS scheduled_job_locks
(
    job_name     VARCHAR(120) PRIMARY KEY,
    locked_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by    VARCHAR(120) NOT NULL
);

CREATE TABLE IF NOT EXISTS privileged_audit_events
(
    id              BIGSERIAL    PRIMARY KEY,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    request_id      VARCHAR(80)   NOT NULL,
    actor           VARCHAR(100)  NOT NULL,
    authorities     VARCHAR(500)  NOT NULL,
    http_method     VARCHAR(10)   NOT NULL,
    request_path    VARCHAR(500)  NOT NULL,
    response_status INT           NOT NULL,
    succeeded       BOOLEAN       NOT NULL,
    source_ip_hash  CHAR(64)      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_privileged_audit_occurred_at
    ON privileged_audit_events (occurred_at);
CREATE INDEX IF NOT EXISTS idx_privileged_audit_actor
    ON privileged_audit_events (actor, occurred_at);

CREATE TABLE IF NOT EXISTS card_encryption_key_checks
(
    id               SMALLINT    PRIMARY KEY,
    encrypted_marker TEXT        NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    verified_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT card_encryption_key_check_singleton CHECK (id = 1)
);

-- A trainer can have several consented gym affiliations. Preserve the original primary-gym field.
CREATE TABLE IF NOT EXISTS trainer_gym_affiliations (
    id BIGSERIAL PRIMARY KEY,
    trainer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id BIGINT NOT NULL REFERENCES gym_profiles(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACTIVE','DECLINED','ENDED')),
    initiated_by VARCHAR(20) NOT NULL CHECK (initiated_by IN ('GYM','TRAINER','LEGACY')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_trainer_gym_affiliation UNIQUE(trainer_user_id, gym_id)
);
CREATE INDEX IF NOT EXISTS idx_gym_affiliation_status ON trainer_gym_affiliations(gym_id, status);
CREATE TABLE IF NOT EXISTS trainer_gym_affiliation_events (
    id BIGSERIAL PRIMARY KEY,
    affiliation_id BIGINT NOT NULL REFERENCES trainer_gym_affiliations(id) ON DELETE CASCADE,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_gym_affiliation_events ON trainer_gym_affiliation_events(affiliation_id, created_at DESC, id DESC);

-- Private qualification evidence and append-only application review snapshots.
CREATE TABLE IF NOT EXISTS trainer_verification_events (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES trainer_verification_requests(id) ON DELETE CASCADE,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(24) NOT NULL CHECK (action IN ('LEGACY_SNAPSHOT','SUBMITTED','RESPONDED','NEEDS_INFO','APPROVED','REJECTED','DOCUMENT_ADDED','DOCUMENT_REMOVED')),
    previous_status VARCHAR(20),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','NEEDS_INFO','APPROVED','REJECTED')),
    trainer_notes TEXT,
    admin_notes TEXT,
    document_name VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_verification_events_request ON trainer_verification_events(request_id, created_at, id);

CREATE TABLE IF NOT EXISTS trainer_verification_documents (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES trainer_verification_requests(id) ON DELETE CASCADE,
    uploaded_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(120) NOT NULL,
    content_type VARCHAR(40) NOT NULL CHECK (content_type IN ('application/pdf','image/png','image/jpeg')),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 2097152),
    content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 2097152),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT verification_document_size_matches CHECK (octet_length(content) = byte_size)
);
CREATE INDEX IF NOT EXISTS idx_verification_documents_request ON trainer_verification_documents(request_id, created_at, id);
