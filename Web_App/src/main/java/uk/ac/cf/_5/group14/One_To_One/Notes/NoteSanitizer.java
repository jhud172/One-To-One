package uk.ac.cf._5.group14.One_To_One.Notes;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class NoteSanitizer {
    private static final Set<String> FORMATTING = Set.of("ql-align-center", "ql-align-right", "ql-align-justify",
            "ql-size-small", "ql-size-large", "ql-size-huge", "note-highlight");
    private final Safelist safelist = Safelist.relaxed().addTags("span", "u")
            .addAttributes(":all", "class").addAttributes("a", "target")
            .addEnforcedAttribute("a", "rel", "noopener noreferrer")
            .addProtocols("a", "href", "http", "https", "mailto").preserveRelativeLinks(true);

    public String sanitize(String html) {
        if (html == null || html.isBlank()) return "";
        var document = Jsoup.parseBodyFragment(html);
        for (Element element : document.getAllElements()) {
            String style = element.attr("style").replace(" ", "").toLowerCase(java.util.Locale.ROOT);
            if (style.contains("text-align:center")) element.addClass("ql-align-center");
            if (style.contains("text-align:right")) element.addClass("ql-align-right");
            if (style.contains("text-align:justify")) element.addClass("ql-align-justify");
            if (style.contains("background-color:rgba(34,211,238,0.25)")) element.addClass("note-highlight");
            if (element.tagName().equals("font")) {
                switch (element.attr("size")) {
                    case "1", "2" -> element.addClass("ql-size-small");
                    case "4" -> element.addClass("ql-size-large");
                    case "5", "6", "7" -> element.addClass("ql-size-huge");
                    default -> { }
                }
                element.tagName("span");
            }
            String classes = element.classNames().stream().filter(FORMATTING::contains)
                    .collect(java.util.stream.Collectors.joining(" "));
            if (classes.isBlank()) element.removeAttr("class"); else element.attr("class", classes);
        }
        return Jsoup.clean(document.body().html(), safelist);
    }
}
