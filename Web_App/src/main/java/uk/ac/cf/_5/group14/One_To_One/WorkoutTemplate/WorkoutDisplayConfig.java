package uk.ac.cf._5.group14.One_To_One.WorkoutTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounded presentation data; no HTML, CSS or executable configuration is accepted. */
@Component
@RequiredArgsConstructor
public class WorkoutDisplayConfig {
    private final ObjectMapper json;
    public static final List<String> COMPONENTS = List.of("progress", "timer", "exerciseCard", "notes", "setEntry", "summary", "restTimer");
    public record Settings(String layout, String theme, String transition, String density,
                           boolean progress, boolean restTimer, List<String> components) {}

    public Settings defaults(TemplateLayoutType layout) {
        boolean plain = layout == TemplateLayoutType.PLAIN;
        return new Settings(layout.name().toLowerCase(Locale.ROOT),
                layout == TemplateLayoutType.FUTURISTIC_FLOW ? "futuristic" : "default",
                "none", plain ? "compact" : "comfortable", !plain, !plain,
                plain ? List.of("exerciseCard", "setEntry") : List.of("progress", "exerciseCard", "setEntry", "restTimer", "summary"));
    }

    public Settings validate(TemplateLayoutType layout, String theme, String transition, String density,
                             boolean progress, boolean restTimer, List<String> components) {
        if (!Set.of("default", "dark-pro", "light-clean", "futuristic").contains(theme)
                || !Set.of("none", "slide", "fade").contains(transition)
                || !Set.of("comfortable", "compact", "spacious").contains(density)
                || components == null || components.size() > COMPONENTS.size()
                || components.stream().anyMatch(c -> !COMPONENTS.contains(c))
                || components.stream().distinct().count() != components.size()) throw invalid();
        return new Settings(layout.name().toLowerCase(Locale.ROOT), theme, transition, density, progress, restTimer, List.copyOf(components));
    }

    public Settings parse(TemplateLayoutType layout, String source) {
        if (source == null || source.isBlank()) return defaults(layout);
        if (source.length() > 16384) throw invalid();
        try {
            JsonNode node = json.readTree(source);
            if (node == null || !node.isObject()) throw invalid();
            Settings base = defaults(layout);
            Set<String> keys = Set.of("layout", "theme", "transition", "density", "progress", "restTimer", "components");
            var fields = node.fieldNames();
            while (fields.hasNext()) if (!keys.contains(fields.next())) throw invalid();
            if (!text(node,"layout",base.layout()).equals(base.layout())) throw invalid();
            List<String> components = new ArrayList<>();
            JsonNode items = node.get("components");
            if (items == null) components.addAll(base.components());
            else {
                if (!items.isArray() || items.size() > COMPONENTS.size()) throw invalid();
                for (JsonNode item : items) { if (!item.isTextual()) throw invalid(); components.add(item.textValue()); }
            }
            return validate(layout, text(node,"theme",base.theme()), text(node,"transition",base.transition()),
                    text(node,"density",base.density()), bool(node,"progress",base.progress()), bool(node,"restTimer",base.restTimer()), components);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }

    public Settings forDisplay(WorkoutTemplate template) {
        TemplateLayoutType type = template.getLayoutType() == null ? TemplateLayoutType.FLOW : template.getLayoutType();
        try { return parse(type, template.getConfigJson()); }
        catch (IllegalArgumentException ex) { return defaults(type); }
    }

    public String serialise(Settings settings) {
        try { return json.writeValueAsString(settings); }
        catch (Exception ex) { throw invalid(); }
    }
    private String text(JsonNode node, String key, String fallback) {
        JsonNode value = node.get(key); if (value == null) return fallback;
        if (!value.isTextual()) throw invalid(); return value.textValue();
    }
    private boolean bool(JsonNode node, String key, boolean fallback) {
        JsonNode value = node.get(key); if (value == null) return fallback;
        if (!value.isBoolean()) throw invalid(); return value.booleanValue();
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid workout display settings"); }
}
