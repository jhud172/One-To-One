package uk.ac.cf._5.group14.One_To_One.Web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SharedLayoutRecoveryIntegrationTest.MissingDecoration.class)
class SharedLayoutRecoveryIntegrationTest {
    @Autowired MockMvc mvc;

    @TestConfiguration
    static class MissingDecoration {
        @Bean WebMvcConfigurer missingGuestDecoration() {
            return new WebMvcConfigurer() {
                @Override public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(new HandlerInterceptor() {
                        @Override public void postHandle(HttpServletRequest request, HttpServletResponse response,
                                Object handler, ModelAndView view) {
                            // Render the actual shared layout with the optional advice value absent.
                            if (view != null && "missing".equals(request.getParameter("decoration"))) {
                                view.getModel().remove("includeGuestExperience");
                            }
                        }
                    });
                }
            };
        }
    }

    @Test void signInStillRendersWithoutOptionalGuestDecoration() throws Exception {
        var response = mvc.perform(get("/login").param("decoration", "missing"))
                .andExpect(status().isOk()).andReturn().getResponse();
        var page = org.jsoup.Jsoup.parse(response.getContentAsString());
        assertThat(page.select("form[action='/login']")).isNotEmpty();
        assertThat(page.select("main#main-content")).hasSize(1);
        assertThat(page.body().hasClass("guest-route")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 429, 500})
    void errorStatusAndUsableRecoveryLinksSurviveMissingGuestFlag(int code) throws Exception {
        var response = mvc.perform(get("/error").param("decoration", "missing")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, code)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing?token=private"))
                .andExpect(status().is(code))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse();
        var page = org.jsoup.Jsoup.parse(response.getContentAsString());
        assertThat(page.select("main#main-content h1")).hasSize(1);
        assertThat(page.select("main a[href='/']")).isNotEmpty();
        assertThat(page.text()).doesNotContain("token=private", "SpelEvaluationException");
    }
}
