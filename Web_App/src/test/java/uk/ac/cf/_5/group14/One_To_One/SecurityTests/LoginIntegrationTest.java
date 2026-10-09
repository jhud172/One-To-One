package uk.ac.cf._5.group14.One_To_One.SecurityTests;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void demoUserCanLoginWithSeededCredentials() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "demo")
                        .param("password", "Demo123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername("demo"));
    }

    @Test
    void trainerCanLoginWithSeededTrainerCode() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "trainer")
                        .param("username", "trainer_demo")
                        .param("trainerCode", "1203-4005-6789")
                        .param("password", "Demo123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername("trainer_demo"));
    }

    @Test
    void gymAdminCanLoginWithSeededGymUsernameAndSecretCode() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "gym")
                        .param("username", "gymadmin_demo")
                        .param("gymSecretCode", "4827-0019-3845-6202")
                        .param("password", "Demo123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername("gymadmin_demo"));
    }

    @Test
    void gymAdminCannotLoginWithSecretCodeInUsernameField() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "gym")
                        .param("username", "4827-0019-3845-6209")
                        .param("gymSecretCode", "4827-0019-3845-6209")
                        .param("password", "Demo123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(unauthenticated());
    }

    @Test
    void loginPageCanPreselectGymRoleFromSignupLink() throws Exception {
        String html = mockMvc.perform(get("/login").param("role", "gym"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-login-server-role=\"gym\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Document document = Jsoup.parse(html);
        assertThat(document.getElementById("emailFields").hasAttr("hidden")).isTrue();
        assertThat(document.getElementById("emailFields").hasAttr("inert")).isTrue();
        assertThat(document.getElementById("gymFields").hasAttr("hidden")).isFalse();
        assertThat(document.getElementById("gymFields").hasAttr("inert")).isFalse();
        assertThat(document.getElementById("trainerCodeField").hasAttr("hidden")).isTrue();
        assertThat(document.getElementById("roleSignupLink").attr("href")).isEqualTo("/signup/gym");
        assertThat(document.getElementById("chatWidget")).isNull();
    }

    @Test
    void nativeRoleFormsHaveOneUsableCredentialSourceAndNoInactiveRequiredFields() throws Exception {
        for (String role : new String[]{"client", "trainer", "gym"}) {
            String html = mockMvc.perform(get("/login").param("role", role))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            Document document = Jsoup.parse(html);
            assertThat(document.select(".auth-native-roles a")).hasSize(3);
            boolean gym = "gym".equals(role);
            assertThat(document.getElementById("username").hasAttr("disabled")).isEqualTo(gym);
            assertThat(document.getElementById("password").hasAttr("disabled")).isEqualTo(gym);
            assertThat(document.getElementById("gymUsername").hasAttr("disabled")).isEqualTo(!gym);
            assertThat(document.getElementById("gymPassword").hasAttr("disabled")).isEqualTo(!gym);
            assertThat(document.getElementById("trainerCodeFull").hasAttr("disabled")).isTrue();
            assertThat(document.getElementById("gymSecretCodeFull").hasAttr("disabled")).isTrue();
            var trainerCode = document.select("noscript input[name=trainerCode]");
            var gymCode = document.select("noscript input[name=gymSecretCode]");
            assertThat(trainerCode).hasSize("trainer".equals(role) ? 1 : 0);
            assertThat(gymCode).hasSize(gym ? 1 : 0);
            if (!trainerCode.isEmpty()) assertThat(trainerCode.first().attr("pattern")).isEqualTo("[A-Za-z0-9]{12}");
            if (!gymCode.isEmpty()) {
                assertThat(gymCode.first().attr("pattern")).isEqualTo("[0-9]{16}");
                assertThat(gymCode.first().attr("type")).isEqualTo("password");
            }
        }
    }

    @Test
    void selectedRoleMustMatchTheAccountType() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "trainer")
                        .param("username", "demo")
                        .param("trainerCode", "1203-4005-6789")
                        .param("password", "Demo123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=invalid&role=trainer"))
                .andExpect(unauthenticated());
    }

    @Test
    void failedGymLoginKeepsRoleAndIdentifierAndMarksTheCodeGroup() throws Exception {
        MvcResult failedLogin = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "gym")
                        .param("username", "gymadmin_demo")
                        .param("gymSecretCode", "0000-0000-0000-0000")
                        .param("password", "Demo123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=invalid&role=gym"))
                .andExpect(unauthenticated())
                .andReturn();

        Cookie sessionCookie = failedLogin.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        String html = mockMvc.perform(get("/login")
                        .param("error", "invalid")
                        .param("role", "gym")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("gym username, secret code and password combination")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Document document = Jsoup.parse(html);
        assertThat(document.getElementById("gymUsername").val()).isEqualTo("gymadmin_demo");
        assertThat(document.selectFirst(".auth-code-fieldset").attr("aria-invalid")).isEqualTo("true");
        assertThat(document.selectFirst(".auth-code-fieldset").attr("aria-describedby")).contains("loginError");
    }

    @Test
    void firstLoginKeepsTheRequestedDestinationWithoutACompulsoryTour() throws Exception {
        jdbcTemplate.update("update users set has_seen_tutorial = false where username = ?", "demo");
        mockMvc.perform(post("/login").with(csrf())
                        .param("loginType", "client").param("username", "demo")
                        .param("password", "Demo123!").param("next", "/goals"))
                .andExpect(redirectedUrl("/goals"));
    }

    @Test
    void returningUsersGoDirectlyToTheirRoleDashboard() throws Exception {
        jdbcTemplate.update("update users set has_seen_tutorial = true where username in (?, ?, ?, ?)",
                "demo", "trainer_demo", "gymadmin_demo", "admin_demo");

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "client")
                        .param("username", "demo")
                        .param("password", "Demo123!"))
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "trainer")
                        .param("username", "trainer_demo")
                        .param("trainerCode", "1203-4005-6789")
                        .param("password", "Demo123!"))
                .andExpect(redirectedUrl("/trainer/dashboard"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "gym")
                        .param("username", "gymadmin_demo")
                        .param("gymSecretCode", "4827-0019-3845-6202")
                        .param("password", "Demo123!"))
                .andExpect(redirectedUrl("/gym/dashboard"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("loginType", "client")
                        .param("username", "admin_demo")
                        .param("password", "Demo123!"))
                .andExpect(redirectedUrl("/admin/dashboard"));
    }
}
