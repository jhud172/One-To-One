package uk.ac.cf._5.group14.One_To_One.Web;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.ServletException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HtmlDoctypeResponseFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageRendersWithHtmlDoctype() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("<!DOCTYPE html>")));
    }

    @Test
    void failedRenderDoesNotCommitPartialHtmlBeforeErrorDispatch() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/broken-page");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> new HtmlDoctypeResponseFilter().doFilter(request, response, (req, res) -> {
            res.setContentType("text/html");
            res.getWriter().write("<html><body>Partial page");
            res.getWriter().flush();
            throw new ServletException("Template render failed");
        })).isInstanceOf(ServletException.class).hasMessage("Template render failed");

        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void byteOrderMarkDoesNotPreventStandardsMode() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/shop");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding("UTF-8");
        new HtmlDoctypeResponseFilter().doFilter(request, response, (req, res) -> {
            res.setContentType("text/html");
            res.getWriter().write("\uFEFF<html><body>Shop</body></html>");
        });
        assertThat(response.getContentAsString()).startsWith("<!DOCTYPE html>").doesNotContain("\uFEFF");
    }
}
