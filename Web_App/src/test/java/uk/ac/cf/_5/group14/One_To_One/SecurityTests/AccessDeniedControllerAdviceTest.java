package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;

import uk.ac.cf._5.group14.One_To_One.Security.AccessDeniedControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;

class AccessDeniedControllerAdviceTest {

    @Test
    void protectedRouteDenialsReceiveGuestStylingWithoutExposingExceptionDetails() {
        ExtendedModelMap model = new ExtendedModelMap();
        model.addAttribute("uiStyleBundles", List.of("/css/bundles/dashboard.css"));

        String view = new AccessDeniedControllerAdvice().handleAccessDeniedException(
                new AccessDeniedException("sensitive authorisation detail"), model);

        assertThat(view).isEqualTo("system-views/error/403");
        assertThat(model.get("uiStyleBundles"))
                .isEqualTo(List.of("/css/bundles/dashboard.css", "/css/bundles/guest.css"));
        assertThat(model.get("includeGuestExperience")).isEqualTo(true);
        assertThat(model.get("currentLanguage")).isNotNull();
        assertThat(model.get("supportedLanguages")).isEqualTo(uk.ac.cf._5.group14.One_To_One.Config.SupportedLanguage.all());
        assertThat(model.get("textDirection")).isIn("ltr", "rtl");
        assertThat(model.asMap()).doesNotContainKey("errorMessage");
    }
}
