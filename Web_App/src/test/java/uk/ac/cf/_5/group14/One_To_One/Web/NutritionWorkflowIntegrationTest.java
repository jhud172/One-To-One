package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Nutrition.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class NutritionWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired DailyNutritionLogRepository logs;
    @Autowired DailyNutritionLogService service;
    User owner, other;
    LocalDate day = LocalDate.now();
    String[] locales = {"en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh"};
    @BeforeEach void setup() { owner = client(); other = client(); }

    @Test void malformedDatesAndValuesKeepLiteralDraftsAndNamedErrorsInFourteenLocales() throws Exception {
        for (String locale : locales) {
            var result = mvc.perform(get("/nutrition").param("date", "bad<date>").param("lang", locale)
                    .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest()).andReturn();
            var html = Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(html.selectFirst("#nutrition-picker-draft").text()).isEqualTo("bad<date>");
            assertThat(html.select("form[data-nutrition-form]")).isEmpty();
            assertThat(html.select("a[href='#nutrition-date']")).hasSize(1);
            result = mvc.perform(nativePost(owner, Map.of("date","bad<date>","calories","bad<intake>",
                    "notes"," line 1\n<literal> "), locale)).andExpect(status().isBadRequest()).andReturn();
            html = Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(html.selectFirst("#nutrition-date-draft").text()).isEqualTo("bad<date>");
            assertThat(html.selectFirst("#calories").val()).isEqualTo("bad<intake>");
            assertThat(html.selectFirst("#notes").wholeText()).isEqualTo(" line 1\n<literal> ");
            assertThat(html.select("#nutrition-errors a")).hasSize(2);
            for (var link : html.select("#nutrition-errors a")) assertThat(html.select(link.attr("href"))).hasSize(1);
            assertThat(html.selectFirst("main").text()).doesNotContain("??ui.nutrition.");
            assertThat(html.select("form[data-nutrition-form] input[name=_csrf]")).hasSize(1);
        }
        assertThat(logs.count()).isZero();
    }

    @Test void staleEmptyAndSavedDraftsRequireReviewAndKeepTheSameOwnedDayAndIdentity() throws Exception {
        String empty = revision(owner, day);
        mvc.perform(nativePost(owner, Map.of("expectedRevision", empty,"notes","First <literal>"),"en"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("nutritionSaved",true));
        logs.flush(); var first = logs.findByUserAndDate(owner,day).orElseThrow(); Long id = first.getId();
        var result = mvc.perform(nativePost(owner, Map.of("expectedRevision",empty,"calories","2100",
                "notes","  retained\n<draft>  "),"en")).andExpect(status().isConflict()).andReturn();
        var html = Jsoup.parse(result.getResponse().getContentAsString());
        assertThat(html.selectFirst("#calories").val()).isEqualTo("2100");
        assertThat(html.selectFirst("#notes").wholeText()).isEqualTo("  retained\n<draft>  ");
        assertThat(html.selectFirst(".nutrition-record-sidebar").text()).contains("2000","First <literal>");
        assertThat(html.selectFirst("form[data-nutrition-form] button[type=submit]").text()).isEqualTo("Save reviewed draft");
        String reviewed = (String) result.getModelAndView().getModel().get("nutritionRevision");
        mvc.perform(nativePost(owner, Map.of("expectedRevision",reviewed,"calories","2100","waterMl","0","notes","  reviewed  "),"en"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/nutrition?date="+day));
        logs.flush(); var saved = logs.findByUserAndDate(owner,day).orElseThrow();
        assertThat(saved.getId()).isEqualTo(id); assertThat(saved.getCalories()).isEqualTo(2100);
        assertThat(saved.getFibreGrams()).isNull(); assertThat(saved.getWaterMl()).isZero(); assertThat(saved.getNotes()).isEqualTo("reviewed");
        mvc.perform(nativePost(owner, Map.of("expectedRevision",reviewed,"calories","2200"),"en")).andExpect(status().isConflict());
        assertThat(saved.getCalories()).isEqualTo(2100); assertThat(logs.count()).isEqualTo(1);
    }

    @Test void missingRevisionForeignTokenAndChangedDateCannotSilentlyOverwriteAnotherEntry() throws Exception {
        String foreign = revision(other,day), own = revision(owner,day);
        mvc.perform(nativePost(owner,Map.of("expectedRevision",foreign),"en")).andExpect(status().isConflict());
        mvc.perform(nativePost(owner,Map.of(),"en")).andExpect(status().isConflict());
        mvc.perform(nativePost(owner,Map.of("expectedRevision",own,"date",day.plusDays(1).toString()),"en")).andExpect(status().isConflict());
        assertThat(logs.count()).isZero();
        mvc.perform(nativePost(owner,Map.of("expectedRevision",own,"id","99","user.id",other.getId().toString()),"en"))
                .andExpect(status().is3xxRedirection()); logs.flush();
        assertThat(logs.findByUserAndDate(other,day)).isEmpty();
        mvc.perform(post("/nutrition").with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isUnauthorized());
        assertThat(logs.count()).isEqualTo(1);
    }

    @Test void invalidDraftKeepsItsOriginalRevisionSoCorrectionCannotBypassAnInterveningSave() throws Exception {
        String empty = revision(owner,day);
        var result = mvc.perform(nativePost(owner,Map.of("expectedRevision",empty,"proteinGrams","1001"),"en"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getModelAndView().getModel().get("nutritionRevision")).isEqualTo(empty);
        service.upsert(owner,day,new DailyNutritionLogService.UpsertRequest(1900,100,200,60,null,null,"Newer entry")); logs.flush();
        mvc.perform(nativePost(owner,Map.of("expectedRevision",empty,"proteinGrams","120"),"en")).andExpect(status().isConflict());
        assertThat(logs.findByUserAndDate(owner,day).orElseThrow().getCalories()).isEqualTo(1900);
    }

    private String revision(User account, LocalDate date) { return service.revision(service.getOrCreateForDate(account,date)); }
    private MockHttpServletRequestBuilder nativePost(User account,Map<String,String> changes,String locale) {
        var fields = new java.util.LinkedHashMap<String,String>(); fields.put("date",day.toString());fields.put("calories","2000");
        fields.put("proteinGrams","120");fields.put("carbsGrams","200");fields.put("fatGrams","60");
        fields.put("fibreGrams","");fields.put("waterMl","");fields.put("notes","");fields.putAll(changes);
        var request = post("/nutrition").param("lang",locale).with(user(account.getUsername()).roles("CLIENT")).with(csrf());
        fields.forEach(request::param);return request;
    }
    private User client() { String name="nutrition-"+UUID.randomUUID();var account=new User(name+"@example.invalid","Local","Client",name,"test-password");account.setRole(Role.CLIENT);return users.saveAndFlush(account); }
}
