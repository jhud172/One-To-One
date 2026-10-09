package uk.ac.cf._5.group14.One_To_One.Security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import uk.ac.cf._5.group14.One_To_One.Config.SupportedLanguage;

@ControllerAdvice
public class AccessDeniedControllerAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDeniedException(AccessDeniedException ignored, Model model) {
        // Exception-handler models do not receive ordinary controller model advice.
        SupportedLanguage language = SupportedLanguage.fromCode(LocaleContextHolder.getLocale().toLanguageTag());
        model.addAttribute("currentLanguage", language);
        model.addAttribute("supportedLanguages", SupportedLanguage.all());
        model.addAttribute("textDirection", language.rightToLeft() ? "rtl" : "ltr");
        List<String> bundles = new ArrayList<>();
        Object existingBundles = model.asMap().get("uiStyleBundles");
        if (existingBundles instanceof List<?> existing) {
            existing.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(bundles::add);
        }
        if (!bundles.contains("/css/bundles/guest.css")) {
            bundles.add("/css/bundles/guest.css");
        }

        model.addAttribute("uiStyleBundles", List.copyOf(bundles));
        model.addAttribute("includeGuestExperience", true);
        return "system-views/error/403";
    }
}
