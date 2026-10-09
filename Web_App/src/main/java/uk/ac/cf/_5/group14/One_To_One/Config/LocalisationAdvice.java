package uk.ac.cf._5.group14.One_To_One.Config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class LocalisationAdvice {
    private static final Set<String> LANGUAGE_FILTERS = Set.of(
            "q", "search", "keyword", "status", "page", "size", "sort", "category",
            "from", "to", "date", "view", "range", "period", "active", "filter");

    public record LanguageQueryParameter(String name, String value) {}

    @ModelAttribute("languageQueryParameters")
    public List<LanguageQueryParameter> languageQueryParameters(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) return List.of();
        return request.getParameterMap().entrySet().stream()
                .filter(entry -> LANGUAGE_FILTERS.contains(entry.getKey()))
                .sorted(java.util.Map.Entry.comparingByKey())
                .flatMap(entry -> java.util.Arrays.stream(entry.getValue()).limit(4)
                        .filter(value -> value != null && value.length() <= 512)
                        .map(value -> new LanguageQueryParameter(entry.getKey(), value)))
                .toList();
    }

    @Bean(name = "messageSource")
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages", "messages-home", "messages-ui", "messages-logo");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }

    @ModelAttribute("supportedLanguages")
    public List<SupportedLanguage> supportedLanguages() {
        return SupportedLanguage.all();
    }

    @ModelAttribute("currentLanguage")
    public SupportedLanguage currentLanguage(Locale locale) {
        return SupportedLanguage.fromCode(locale == null ? null : locale.toLanguageTag());
    }

    @ModelAttribute("textDirection")
    public String textDirection(Locale locale) {
        return currentLanguage(locale).rightToLeft() ? "rtl" : "ltr";
    }
}
