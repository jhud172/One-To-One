package uk.ac.cf._5.group14.One_To_One.ChatV2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatV2IconRegistry {

    private static final Map<String, String> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("sparkles", "\u2728");
        ICONS.put("bolt", "\u26a1");
        ICONS.put("leaf", "\ud83c\udf3f");
        ICONS.put("calendar", "\ud83d\udcc5");
        ICONS.put("note", "\ud83d\udcdd");
        ICONS.put("target", "\ud83c\udfaf");
        ICONS.put("chat", "\ud83d\udcac");
        ICONS.put("heart", "\u2764\ufe0f");
    }

    private ChatV2IconRegistry() {
    }

    public static List<String> keys() {
        return List.copyOf(ICONS.keySet());
    }

    public static Map<String, String> iconMap() {
        return Map.copyOf(ICONS);
    }

    public static String iconFor(String key) {
        return ICONS.getOrDefault(key, "\ud83d\udcac");
    }
}
