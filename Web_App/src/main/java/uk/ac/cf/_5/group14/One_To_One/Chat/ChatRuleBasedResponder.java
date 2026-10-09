package uk.ac.cf._5.group14.One_To_One.Chat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ChatRuleBasedResponder {

    private ChatRuleBasedResponder() {}

    public static String respondPublic(String userMessage) {
        String msg = userMessage == null ? "" : userMessage.trim();
        String lower = msg.toLowerCase(Locale.ROOT);

        if (isGreeting(lower)) {
            return "Hi, I am Charlie. I can explain One To One, help you decide whether it fits you, compare client, trainer, and gym accounts, and point you to the right place to start. What are you trying to do: train, coach clients, or manage a gym?";
        }

        if (containsAny(lower, "why", "benefit", "worth", "use one to one", "use 1 to one", "use one-to-one", "use 1-to-1", "better than", "different from")) {
            return "Use One To One if you want training to feel structured rather than scattered. The platform is built around verified trainers, one active trainer relationship per client, in-platform payments, calendars, progress logs, messaging, and Charlie guidance. For clients, that means clearer accountability. For trainers, it means less admin and cleaner client management. For gyms, it means more control over trainer operations and member experience.";
        }

        if (containsAny(lower, "what is one to one", "what is 1 to one", "what's one to one", "explain one to one", "explain 1 to one")) {
            return "One To One is a premium fitness coaching platform. Clients can find verified trainers, organise training, track progress, and keep communication in one place. Trainers can manage clients, plans, schedules, and coaching workflows. Gyms can manage trainer operations and premium member experiences.";
        }

        if (containsAny(lower, "who is it for", "who should use", "is it for me", "beginner", "new to gym", "new to fitness")) {
            return "One To One is for people who want structure and accountability. It fits beginners who need a safe starting point, regular gym users who want a proper plan, trainers who need a professional client-management workspace, and gyms that want verified trainer operations. If you are unsure where to start, begin as a client and explore trainers. [NAV:/signup/client:Start as client]";
        }

        if (lower.contains("price") || lower.contains("pricing") || lower.contains("cost") || lower.contains("pro") || lower.contains("premium")) {
            return "One To One has a public pricing page where you can compare Starter and Pro access. Pro unlocks personalised Charlie guidance, richer training insights, and more coaching support. Use the Pricing button to review the options. [NAV:/pricing:Pricing]";
        }

        if (lower.contains("trainer") || lower.contains("coach")) {
            return "Clients use One To One to find verified trainers, keep training organised, and stay accountable. Trainers use it to manage clients, plans, calendars, and coaching communication. Use the Explore trainers button to browse public trainer information. [NAV:/explore:Explore trainers]";
        }

        if (lower.contains("gym")) {
            return "Gym accounts are designed for managing trainer operations, memberships, and verified coaching workflows. A gym can oversee trainers while clients still keep a focused one-to-one coaching relationship.";
        }

        if (containsAny(lower, "client", "member", "customer")) {
            return "As a client, One To One helps you find a verified trainer, keep your plan organised, use a calendar for training and tasks, log progress, message around coaching, and stay focused on one active trainer relationship at a time. That avoids the usual mix of scattered apps, notes, payments, and messages.";
        }

        if (containsAny(lower, "payment", "pay", "subscription", "stripe", "money")) {
            return "Payments are intended to stay inside One To One. That protects the client relationship, gives trainers a cleaner business workflow, and helps gyms or platform admins keep a reliable record of access and subscriptions. You can compare public options on Pricing. [NAV:/pricing:Pricing]";
        }

        if (containsAny(lower, "safe", "trust", "verified", "privacy", "gdpr", "secure")) {
            return "The trust model is a core part of One To One: trainers are verified, clients keep one active trainer relationship at a time, payments stay in-platform, and privacy matters because training, health, and account data are sensitive. Charlie can guide users, but account-specific help only becomes personalised after login.";
        }

        if (containsAny(lower, "charlie", "ai", "assistant", "chatbot")) {
            return "Charlie is the One To One assistant. On public pages I can explain the platform and help visitors choose the right path. After login, Charlie can use account context such as role, schedule, workouts, and progress to give more specific support.";
        }

        if (lower.contains("sign up") || lower.contains("signup") || lower.contains("register") || lower.contains("join")) {
            return "You can create an account as a client, trainer, or gym. Use the Sign up button, then choose the account type that matches how you want to use One To One. [NAV:/signup:Sign up]";
        }

        if (lower.contains("login") || lower.contains("log in")) {
            return "Use the Login button to access your account. Once you are signed in, Charlie can use your role and training context to give more specific guidance. [NAV:/login:Login]";
        }

        if (lower.contains("feature") || lower.contains("what can") || lower.contains("how does") || lower.contains("platform")) {
            return "One To One is a premium coaching platform for clients, verified trainers, and gyms. It covers trainer discovery, coaching relationships, calendar planning, progress tracking, messaging, payments, and AI guidance through Charlie.";
        }

        if (lower.contains("workout") || lower.contains("training") || lower.contains("plan")) {
            return "For training, One To One keeps workouts, calendar tasks, progress logs, and coach communication in one place. Public users can learn how it works, while signed-in clients and trainers get personalised planning and progress context.";
        }

        return "For that, the most useful way to think about One To One is as a structured coaching workspace rather than just another fitness app. It connects verified trainers, clients, gyms, calendars, progress, messaging, payments, and Charlie guidance around the coaching relationship. If you tell me whether you are a client, trainer, or gym, I can tailor the answer.";
    }

    private static boolean isGreeting(String lower) {
        String normalized = lower == null ? "" : lower.replaceAll("[^a-z0-9 ]", "").trim();
        return normalized.equals("hi")
                || normalized.equals("hello")
                || normalized.equals("hey")
                || normalized.equals("hello charlie")
                || normalized.equals("hi charlie")
                || normalized.equals("hey charlie");
    }

    private static boolean containsAny(String lower, String... terms) {
        if (lower == null || terms == null) {
            return false;
        }
        for (String term : terms) {
            if (term != null && !term.isBlank() && lower.contains(term)) {
                return true;
            }
        }
        return false;
    }

    public static String respond(String userMessage, ChatContext ctx) {
        String msg = userMessage == null ? "" : userMessage.trim();
        String lower = msg.toLowerCase(Locale.ROOT);

        if (ctx == null) {
            return "I can help, but I don't have your in-app context right now.";
        }

        if (lower.contains("what should i do today") || (lower.contains("today") && lower.contains("do"))) {
            return buildTodayPlan(ctx);
        }

        if (lower.contains("last 3 workout") || lower.contains("last three workout") || lower.contains("summarise my last 3")) {
            return buildRecentWorkouts(ctx);
        }

        if (lower.contains("this week") || lower.contains("how am i doing") || lower.contains("7 day") || lower.contains("seven day") || lower.contains("last 7")) {
            return buildWeekInsights(ctx);
        }

        if (lower.contains("last 30") || lower.contains("30 day") || lower.contains("thirty day") || lower.contains("this month")) {
            return buildMonthInsights(ctx);
        }

        if (lower.contains("supplement") || lower.contains("bcaa") || lower.contains("creatine") || lower.contains("protein powder") || lower.contains("pre-workout") || lower.contains("pre workout")) {
            return buildSupplementAdvice(lower);
        }

        boolean askingExercise = lower.contains("exercise") || lower.contains("workout") || lower.contains("training");
        if (askingExercise && (lower.contains("chest") || lower.contains("back") || lower.contains("shoulder") || lower.contains("leg") || lower.contains("arm") || lower.contains("core") || lower.contains("glute") || lower.contains("bicep") || lower.contains("tricep"))) {
            return buildExerciseSuggestions(lower);
        }

        if ((lower.contains("variation") || lower.contains("alternative") || lower.contains("different") || lower.contains("mix")) &&
                (lower.contains("exercise") || lower.contains("workout"))) {
            return buildVariationSuggestions(ctx);
        }

        if (ctx.requestedDate() != null && (lower.contains("workout") || lower.contains("scheduled") || lower.contains("schedule"))) {
            return buildRequestedDate(ctx);
        }

        // Default: give a compact context-based hint
        return buildTodayPlan(ctx);
    }

    private static String buildTodayPlan(ChatContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Today (" + ctx.today() + "):\n");

        if (ctx.todaysTasks() != null && !ctx.todaysTasks().isEmpty()) {
            sb.append("Tasks:\n");
            for (String t : ctx.todaysTasks()) sb.append("- ").append(t).append("\n");
        } else {
            sb.append("Tasks: none found.\n");
        }

        if (ctx.todaysScheduledItems() != null && !ctx.todaysScheduledItems().isEmpty()) {
            sb.append("Training:\n");
            for (String w : ctx.todaysScheduledItems()) sb.append("- ").append(w).append("\n");
        } else {
            sb.append("Training: nothing scheduled.\n");
        }

        if (ctx.level() != null || ctx.points() != null) {
            sb.append("\nStats: level ").append(ctx.level() != null ? ctx.level() : "?")
              .append(", points ").append(ctx.points() != null ? ctx.points() : "?").append(".");
        }

        return sb.toString().trim();
    }

    private static String buildRecentWorkouts(ChatContext ctx) {
        if (ctx.recentWorkouts() == null || ctx.recentWorkouts().isEmpty()) {
            return "I can't see any recent workout sessions yet.";
        }
        StringBuilder sb = new StringBuilder("Your last workouts:\n");
        for (var w : ctx.recentWorkouts()) {
            sb.append("- ").append(w.date()).append(" - ").append(w.name())
              .append(" (sets=").append(w.totalSets()).append(", completed=").append(w.completed()).append(")\n");
        }
        return sb.toString().trim();
    }

    private static String buildRequestedDate(ChatContext ctx) {
        LocalDate d = ctx.requestedDate();
        String pretty = d.format(DateTimeFormatter.ofPattern("d MMM uuuu"));
        if (ctx.requestedDateItems() == null || ctx.requestedDateItems().isEmpty()) {
            return "For " + pretty + ", I don't see any scheduled tasks or training items.";
        }
        StringBuilder sb = new StringBuilder("For " + pretty + ":\n");
        for (String i : ctx.requestedDateItems()) sb.append("- ").append(i).append("\n");
        return sb.toString().trim();
    }

    private static String buildThisWeekHint(ChatContext ctx) {
        // Keep simple without heavy queries; steer user with what we have.
        StringBuilder sb = new StringBuilder();
        sb.append("I can summarise your week if you tell me which dates you mean (e.g. Mon-Sun).\n");
        sb.append("Right now I can see today and your recent workouts.\n\n");
        sb.append(buildRecentWorkouts(ctx));
        return sb.toString().trim();
    }

    private static String buildWeekInsights(ChatContext ctx) {
        if (ctx.multiDayInsights() != null) {
            var ins = ctx.multiDayInsights();
            StringBuilder sb = new StringBuilder("ðŸ“Š Last 7 days:\n");
            sb.append("- Tasks: ").append(ins.tasksCompleted()).append("/").append(ins.tasksTotal()).append(" completed\n");
            sb.append("- Workouts: ").append(ins.workoutsCompleted()).append("/").append(ins.workoutsTotal()).append(" completed");
            if (ins.missedSessions() > 0) sb.append(", ").append(ins.missedSessions()).append(" missed");
            sb.append("\n");
            if (ins.trendNote() != null) sb.append(ins.trendNote()).append("\n");
            return sb.toString().trim();
        }
        return buildThisWeekHint(ctx);
    }

    private static String buildMonthInsights(ChatContext ctx) {
        // Context only carries 7-day; give a note and use recent workouts
        StringBuilder sb = new StringBuilder("ðŸ“… 30-day summary isn't fully loaded in this mode.\n");
        sb.append("Here's what I can see from your recent workouts:\n\n");
        sb.append(buildRecentWorkouts(ctx));
        return sb.toString().trim();
    }

    private static String buildSupplementAdvice(String lower) {
        if (lower.contains("creatine")) {
            return "ðŸ’Š Creatine:\n" +
                   "- Loading phase: 20g/day for 5–7 days (split into 4 x 5g doses)\n" +
                   "- Maintenance: 3–5g/day\n" +
                   "- Timing: anytime; consistency matters more than timing\n" +
                   "- Evidence: well-researched for strength and power output\n" +
                   "- Take with carbs post-workout for slightly better uptake";
        }
        if (lower.contains("bcaa")) {
            return "ðŸ’Š BCAAs (Leucine, Isoleucine, Valine):\n" +
                   "- Most useful if training fasted or have low protein intake\n" +
                   "- Dose: 5–10g around training\n" +
                   "- Leucine (key for muscle protein synthesis): aim for â‰¥2.5g per dose\n" +
                   "- If you hit your daily protein target (~1.6–2.2g/kg), BCAAs add little benefit";
        }
        if (lower.contains("protein")) {
            return "ðŸ¥© Protein:\n" +
                   "- Daily target: 1.6–2.2g per kg bodyweight for muscle gain\n" +
                   "- Post-workout window: 20–40g within 2 hours\n" +
                   "- Distribute across 3–4 meals for optimal muscle protein synthesis";
        }
        if (lower.contains("pre-workout") || lower.contains("pre workout") || lower.contains("caffeine")) {
            return "âš¡ Pre-workout / Caffeine:\n" +
                   "- Effective dose: 3–6mg caffeine per kg bodyweight\n" +
                   "- Take 30–45 minutes before training\n" +
                   "- Avoid within 6 hours of sleep\n" +
                   "- Cycle off for 2 weeks every 8–12 weeks to maintain sensitivity";
        }
        return "ðŸ’Š Common supplements:\n" +
               "- Creatine: proven for strength/power, 3–5g/day maintenance\n" +
               "- Protein powder: to hit daily protein targets\n" +
               "- BCAAs: useful if training fasted\n" +
               "- Caffeine: 3–6mg/kg pre-workout\n" +
               "- Omega-3: anti-inflammatory, 1–3g EPA+DHA/day\n" +
               "- Vitamin D: 1000–2000 IU/day, especially in low-sun climates\n" +
               "Ask me about a specific supplement for more detail!";
    }

    private static String buildExerciseSuggestions(String lower) {
        StringBuilder sb = new StringBuilder();
        if (lower.contains("chest")) {
            sb.append("ðŸ‹ Chest exercises:\n");
            sb.append("- Barbell bench press (intermediate) – compound push\n");
            sb.append("- Dumbbell flyes (beginner) – isolation stretch\n");
            sb.append("- Push-ups (beginner) – bodyweight compound\n");
            sb.append("- Incline dumbbell press (intermediate) – upper chest\n");
            sb.append("- Cable crossover (intermediate) – chest squeeze\n");
            sb.append("ðŸ“º Demo: search 'chest workout tutorial' on YouTube for form guides");
        } else if (lower.contains("back")) {
            sb.append("ðŸ‹ Back exercises:\n");
            sb.append("- Pull-ups / chin-ups (intermediate) – lat width\n");
            sb.append("- Barbell rows (intermediate) – thickness\n");
            sb.append("- Lat pulldown (beginner) – lat width\n");
            sb.append("- Seated cable row (beginner) – mid-back\n");
            sb.append("- Deadlift (advanced) – full posterior chain\n");
            sb.append("ðŸ“º Demo: search 'back workout tutorial' on YouTube for form guides");
        } else if (lower.contains("shoulder")) {
            sb.append("ðŸ‹ Shoulder exercises:\n");
            sb.append("- Overhead press (intermediate) – all deltoid heads\n");
            sb.append("- Lateral raises (beginner) – medial delts\n");
            sb.append("- Face pulls (beginner) – rear delts & rotator cuff\n");
            sb.append("- Arnold press (intermediate) – full range\n");
            sb.append("ðŸ“º Demo: search 'shoulder workout tutorial' on YouTube for form guides");
        } else if (lower.contains("leg")) {
            sb.append("ðŸ‹ Leg exercises:\n");
            sb.append("- Back squat (intermediate) – quads, glutes\n");
            sb.append("- Romanian deadlift (intermediate) – hamstrings, glutes\n");
            sb.append("- Leg press (beginner) – quads\n");
            sb.append("- Walking lunges (beginner) – unilateral legs\n");
            sb.append("- Leg curl (beginner) – hamstrings isolation\n");
            sb.append("ðŸ“º Demo: search 'leg workout tutorial' on YouTube for form guides");
        } else if (lower.contains("core")) {
            sb.append("ðŸ‹ Core exercises:\n");
            sb.append("- Plank (beginner) – anti-extension\n");
            sb.append("- Dead bug (beginner) – core stability\n");
            sb.append("- Hanging leg raise (intermediate) – lower abs\n");
            sb.append("- Cable woodchop (intermediate) – rotational core\n");
            sb.append("ðŸ“º Demo: search 'core workout tutorial' on YouTube for form guides");
        } else if (lower.contains("glute")) {
            sb.append("ðŸ‹ Glute exercises:\n");
            sb.append("- Hip thrust (intermediate) – glute max\n");
            sb.append("- Glute bridge (beginner) – beginner hip thrust\n");
            sb.append("- Bulgarian split squat (intermediate) – glutes + quads\n");
            sb.append("- Cable kickbacks (beginner) – glute isolation\n");
            sb.append("ðŸ“º Demo: search 'glute workout tutorial' on YouTube for form guides");
        } else if (lower.contains("bicep") || lower.contains("arm")) {
            sb.append("ðŸ‹ Bicep / arm exercises:\n");
            sb.append("- Barbell curl (beginner) – peak bicep\n");
            sb.append("- Incline dumbbell curl (intermediate) – long head stretch\n");
            sb.append("- Hammer curl (beginner) – brachialis\n");
            sb.append("- Preacher curl (beginner) – short head\n");
            sb.append("ðŸ“º Demo: search 'bicep curl tutorial' on YouTube for form guides");
        } else if (lower.contains("tricep")) {
            sb.append("ðŸ‹ Tricep exercises:\n");
            sb.append("- Close-grip bench press (intermediate) – mass builder\n");
            sb.append("- Skull crushers (intermediate) – long head\n");
            sb.append("- Cable pushdown (beginner) – lateral head\n");
            sb.append("- Overhead cable extension (beginner) – long head stretch\n");
            sb.append("ðŸ“º Demo: search 'tricep workout tutorial' on YouTube for form guides");
        } else {
            sb.append("ðŸ’¡ Specify a muscle group for targeted suggestions:\n");
            sb.append("chest, back, shoulders, legs, core, glutes, biceps, triceps");
        }
        return sb.toString().trim();
    }

    private static String buildVariationSuggestions(ChatContext ctx) {
        if (ctx.recentWorkouts() == null || ctx.recentWorkouts().isEmpty()) {
            return "I don't see any recent workout history. Once you've logged some sessions, I can suggest variations to keep things fresh!";
        }
        StringBuilder sb = new StringBuilder("ðŸ”„ Workout variations to keep things fresh:\n");
        sb.append("Based on your recent sessions, here are alternatives:\n\n");
        sb.append("- Swap barbell movements for dumbbell versions (e.g., DB bench instead of BB bench)\n");
        sb.append("- Replace machine exercises with cable alternatives for more range of motion\n");
        sb.append("- Try unilateral movements (single-leg, single-arm) to address imbalances\n");
        sb.append("- Use bodyweight alternatives on deload weeks\n");
        sb.append("- Rotate between horizontal and vertical pulling patterns for back variety\n\n");
        sb.append("Your last " + ctx.recentWorkouts().size() + " sessions: ");
        sb.append(ctx.recentWorkouts().stream()
                .map(w -> w.name() + " (" + w.date() + ")")
                .collect(java.util.stream.Collectors.joining(", ")));
        return sb.toString().trim();
    }
}
