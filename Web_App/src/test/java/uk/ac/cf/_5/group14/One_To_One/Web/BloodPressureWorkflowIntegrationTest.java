package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.time.LocalTime;
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
import uk.ac.cf._5.group14.One_To_One.Health.BloodPressure.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class BloodPressureWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BloodPressureReadingRepository readings;
    @Autowired BloodPressureService service;
    User owner,other;
    LocalDate day=LocalDate.now();
    String[] locales={"en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh"};
    @BeforeEach void setup(){owner=client();other=client();}

    @Test void ownedHistoryHasStableDateTimeIdPagesAndSummaryPeriodIsDistinctInFourteenLocales() throws Exception {
        var ids=new java.util.ArrayList<Long>();for(int i=0;i<7;i++) ids.add(reading(owner,day,LocalTime.of(9,30)).getId());
        var untimed=reading(owner,day,null);reading(other,day,LocalTime.of(9,30));reading(owner,day.minusDays(10),null);
        assertThat(service.history(owner,0).getContent()).extracting(BloodPressureReading::getId)
                .containsExactly(ids.get(6),ids.get(5),ids.get(4),ids.get(3),ids.get(2),ids.get(1));
        assertThat(service.history(owner,1).getContent()).extracting(BloodPressureReading::getId).startsWith(ids.getFirst(),untimed.getId());
        assertThat(service.history(owner,9999).getNumber()).isEqualTo(1);
        assertThat(service.history(other,-1).getTotalElements()).isEqualTo(1);
        for(String locale:locales){
            var result=mvc.perform(get("/health/blood-pressure").param("range","7").param("lang",locale)
                    .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn();
            var html=Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(html.select(".bp-history-card")).hasSize(6);assertThat(html.selectFirst("main").text()).doesNotContain("??ui.bp.","NaN","Infinity");
            assertThat(html.selectFirst("#arm option[value=LEFT]").text()).isNotEqualTo("LEFT");
            assertThat(html.selectFirst("#position option[value=SITTING]").text()).isNotEqualTo("SITTING");
            assertThat(html.selectFirst("a[href*='page=2']").attr("href")).contains("range=7");
            assertThat(html.select("form[data-bp-reading-form] input[name=_csrf]")).hasSize(1);
            assertThat(((BloodPressureService.BpStats)result.getModelAndView().getModel().get("stats")).totalReadings()).isEqualTo(8);
        }
    }

    @Test void invalidNativeCreateAndEditKeepLiteralDraftsNamedErrorLinksAndOptionalContext() throws Exception {
        var saved=reading(owner,day,LocalTime.of(9,30));String revision=service.revision(saved);
        for(String locale:locales){
            var result=mvc.perform(nativePost("/health/blood-pressure",Map.of("readingDate","bad<date>","readingTime","bad<time>",
                    "systolic","bad<pressure>","arm","bad<arm>","position","bad<position>","notes","line 1\n<literal>"),locale))
                    .andExpect(status().isBadRequest()).andReturn();
            var html=Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(html.selectFirst("#systolic").val()).isEqualTo("bad<pressure>");
            assertThat(html.selectFirst("main").text()).contains("bad<date>","bad<time>");
            assertThat(html.select("#bp-errors a")).hasSize(5);
            for(var link:html.select("#bp-errors a")) assertThat(html.select(link.attr("href"))).hasSize(1);
            assertThat(html.selectFirst("#bp-arm-draft").text()).isEqualTo("bad<arm>");
            assertThat(html.selectFirst("#bp-position-draft").text()).isEqualTo("bad<position>");
            assertThat(html.selectFirst("#notes").wholeText()).isEqualTo("line 1\n<literal>");
            assertThat(html.selectFirst("details[open]")).isNotNull();
            result=mvc.perform(nativePost("/health/blood-pressure/edit/"+saved.getId(),Map.of("systolic","59","pulse","29",
                    "expectedRevision",revision,"range","7","page","2"),locale)).andExpect(status().isBadRequest()).andReturn();
            html=Jsoup.parse(result.getResponse().getContentAsString());assertThat(html.select("#bp-errors a")).hasSize(2);
            assertThat(html.selectFirst("#systolic").attr("aria-describedby")).contains("bp-systolic-error");
            assertThat(html.selectFirst("a[data-bp-cancel]").attr("href")).contains("range=7","page=2");
            assertThat(service.findById(saved.getId()).orElseThrow().getSystolic()).isEqualTo(120);
        }
        assertThat(readings.count()).isEqualTo(1);
    }

    @Test void staleEditAndDeletePreserveLatestValuesReviewedSaveKeepsIdentityAndForeignAccessCannotMutate() throws Exception {
        var saved=reading(owner,day,LocalTime.of(9,30));saved.setSource(BloodPressureReading.ReadingSource.IMPORTED);readings.saveAndFlush(saved);
        String old=service.revision(saved);var newer=new BloodPressureReading();newer.setReadingDate(day);newer.setReadingTime(LocalTime.of(9,30));
        newer.setSystolic(125);newer.setDiastolic(82);newer.setNotes("Latest saved <literal>");service.update(saved.getId(),newer,owner,old);readings.flush();
        var result=mvc.perform(nativePost("/health/blood-pressure/edit/"+saved.getId(),Map.of("expectedRevision",old,
                "systolic","130","notes","  retained\n<draft>  ","range","7","page","2"),"en"))
                .andExpect(status().isConflict()).andReturn();var html=Jsoup.parse(result.getResponse().getContentAsString());
        assertThat(html.selectFirst(".bp-conflict").text()).contains("125 / 82","Latest saved <literal>");
        assertThat(html.selectFirst("#systolic").val()).isEqualTo("130");assertThat(html.selectFirst("#notes").wholeText()).isEqualTo("  retained\n<draft>  ");
        assertThat(html.selectFirst("form[data-bp-reading-form] button[type=submit]").text()).isEqualTo("Save reviewed draft");
        var current=service.findById(saved.getId()).orElseThrow();String fresh=service.revision(current);assertThat(current.getSystolic()).isEqualTo(125);
        mvc.perform(post("/health/blood-pressure/delete/{id}",saved.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("expectedRevision",old)).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("bpChanged",true));
        assertThat(service.findById(saved.getId())).isPresent();
        mvc.perform(nativePost("/health/blood-pressure/edit/"+saved.getId(),Map.of("expectedRevision",fresh,"systolic","130","range","7","page","2"),"en"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/health/blood-pressure?range=7&page=2"));
        current=service.findById(saved.getId()).orElseThrow();assertThat(current.getSystolic()).isEqualTo(130);
        assertThat(current.getSource()).isEqualTo(BloodPressureReading.ReadingSource.IMPORTED);assertThat(readings.count()).isEqualTo(1);
        for(String route:new String[]{"/health/blood-pressure/edit/","/health/blood-pressure/delete/"}) {
            mvc.perform(post(route+saved.getId()).with(user(other.getUsername()).roles("CLIENT")).with(csrf())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/health/blood-pressure/edit/{id}",saved.getId()).with(user(other.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        mvc.perform(post("/health/blood-pressure/delete/{id}",saved.getId()).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isUnauthorized());
        assertThat(service.findById(saved.getId())).isPresent();
        mvc.perform(post("/health/blood-pressure/delete/{id}",saved.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("expectedRevision",service.revision(current))).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("bpDeleted",true));
        assertThat(service.findById(saved.getId())).isEmpty();assertThat(service.computeStats(service.getRange(owner,day,day)).totalReadings()).isZero();
    }

    @Test void duplicateUntimedReadingReturnsRetainedNativeErrorAndTimeAllowsSecondReading() throws Exception {
        mvc.perform(nativePost("/health/blood-pressure",Map.of("readingTime",""),"en")).andExpect(status().is3xxRedirection());
        var result=mvc.perform(nativePost("/health/blood-pressure",Map.of("readingTime","","notes","Retained duplicate <literal>"),"en"))
                .andExpect(status().isBadRequest()).andReturn();var html=Jsoup.parse(result.getResponse().getContentAsString());
        assertThat(html.selectFirst("#bp-errors").text()).contains("without a time already exists");
        assertThat(html.select("#bp-errors a[href='#readingTime']")).hasSize(1);
        assertThat(html.selectFirst("#notes").wholeText()).isEqualTo("Retained duplicate <literal>");assertThat(readings.count()).isEqualTo(1);
        mvc.perform(nativePost("/health/blood-pressure",Map.of(),"en")).andExpect(status().is3xxRedirection());assertThat(readings.count()).isEqualTo(2);
        mvc.perform(get("/health/blood-pressure").accept(org.springframework.http.MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
    }

    private MockHttpServletRequestBuilder nativePost(String route,Map<String,String> changes,String locale){
        var fields=new java.util.LinkedHashMap<String,String>();fields.put("readingDate",day.toString());fields.put("readingTime","09:30");
        fields.put("systolic","120");fields.put("diastolic","80");fields.put("pulse","");fields.put("arm","");fields.put("position","");fields.put("notes","");fields.putAll(changes);
        var request=post(route).param("lang",locale).with(user(owner.getUsername()).roles("CLIENT")).with(csrf());fields.forEach(request::param);return request;
    }
    private User client(){String name="bp-"+UUID.randomUUID();var user=new User(name+"@example.invalid","Local","Client",name,"test-password");user.setRole(Role.CLIENT);return users.saveAndFlush(user);}
    private BloodPressureReading reading(User user,LocalDate date,LocalTime time){var reading=new BloodPressureReading();reading.setUser(user);reading.setReadingDate(date);reading.setReadingTime(time);reading.setSystolic(120);reading.setDiastolic(80);return readings.saveAndFlush(reading);}
}
