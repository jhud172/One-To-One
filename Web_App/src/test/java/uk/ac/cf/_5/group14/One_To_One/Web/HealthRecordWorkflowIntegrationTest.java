package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.*;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.PhysicalCondition.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HealthRecordWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired HealthRecordRepository records;
    @Autowired HealthRecordService service;
    @Autowired PhysicalConditionRepository conditions;
    @Autowired uk.ac.cf._5.group14.One_To_One.ConditionsPreferences.UserPreference.UserPreferenceService preferences;
    User owner,other;
    LocalDate day=LocalDate.of(2026,10,4);
    String[] locales={"en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh"};

    @BeforeEach void setup() {owner=client();other=client();}

    @Test void historyUsesOwnedStableDatabasePagesLiteralSearchInclusiveDatesAndMissingActivity() {
        var ids=new java.util.ArrayList<Long>();
        for(int i=0;i<7;i++) ids.add(record(owner,day.atTime(23,59,59),"Legacy %_! activity").getId());
        record(other,day.atTime(23,59,59),"Legacy %_! activity");record(owner,day.minusDays(1).atStartOfDay(),"Sedentary");
        record(owner,day.minusDays(2).atStartOfDay(),null);
        var newest=service.searchHistory(owner,"%_!",day,day,"",false,0);
        assertThat(newest.getTotalElements()).isEqualTo(7);assertThat(newest.getTotalPages()).isEqualTo(2);
        assertThat(newest.getContent()).extracting(HealthRecord::getId).containsExactly(ids.get(6),ids.get(5),ids.get(4),ids.get(3),ids.get(2),ids.get(1));
        assertThat(service.searchHistory(owner,"%_!",day,day,"",true,0).getContent()).extracting(HealthRecord::getId)
                .containsExactly(ids.get(0),ids.get(1),ids.get(2),ids.get(3),ids.get(4),ids.get(5));
        assertThat(service.searchHistory(owner,"%_!",day,day,"",false,9999).getContent()).extracting(HealthRecord::getId).containsExactly(ids.getFirst());
        assertThat(service.searchHistory(owner,"missing",null,null,"",false,9999).getNumber()).isZero();
        assertThat(service.searchHistory(owner,"",null,null,"Sedentary",false,0).getTotalElements()).isEqualTo(1);
        assertThat(service.searchHistory(owner,"",null,null,"",true,0).getTotalElements()).isEqualTo(9);
        assertThatThrownBy(()->service.searchHistory(owner,"",null,null,"Unsupported choice",false,0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectedNativeDraftNamesFieldsRetainsRawInputAndRejectsUnavailableConditionWithoutWriting() throws Exception {
        var rejected=mvc.perform(validPost(java.util.Map.of("baselineDate","bad-date<literal>","weightKg","bad-weight<literal>",
                "activityLevel","Unsupported <activity>")))
                .andExpect(status().isBadRequest()).andReturn();
        var html=Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(html.selectFirst("#weightKg").val()).isEqualTo("bad-weight<literal>");
        assertThat(html.selectFirst("#health-date-draft").text()).isEqualTo("bad-date<literal>");
        assertThat(html.select("#health-errors a")).hasSize(3);
        for(var link:html.select("#health-errors a")) assertThat(html.select(link.attr("href"))).hasSize(1);
        assertThat(html.select("form[data-health-record-form] input[name=_csrf]")).hasSize(1);
        assertThat(html.selectFirst("#weightKg").attr("aria-describedby")).contains("health-weightKg-error");
        var missing=mvc.perform(validPost().param("physicalConditions",Long.MAX_VALUE+""))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("healthRecordForm","physicalConditions")).andReturn();
        html=Jsoup.parse(missing.getResponse().getContentAsString());
        assertThat(html.select("#health-errors a[href='#physicalConditions']")).hasSize(1);
        assertThat(html.selectFirst("#weightKg").val()).isEqualTo("72.0");
        assertThat(records.findAllByUser(owner)).isEmpty();
    }

    @Test void actualSavedValuesConditionsAndLegacyMissingFieldsRenderPrivatelyInFourteenLocales() throws Exception {
        var condition=new PhysicalCondition();condition.setName("Synthetic condition <literal>");condition=conditions.saveAndFlush(condition);
        mvc.perform(validPost().param("physicalConditions",condition.getId().toString())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/health-record/list?success")).andExpect(flash().attribute("healthRecordSaved",true));
        var saved=records.findAllByUser(owner).getFirst();
        assertThat(saved.getBmi()).isEqualTo(22.22);assertThat(saved.getWaistHeightRatio()).isEqualTo(0.46);
        assertThat(saved.getPhysicalConditions()).extracting(PhysicalCondition::getId).containsExactly(condition.getId());
        assertThat(preferences.getLockedConditions(owner)).containsExactly(condition.getId());
        var legacy=record(owner,day.minusDays(1).atStartOfDay(),null);legacy.setSystolicBloodPressure(null);legacy.setDiastolicBloodPressure(null);
        legacy.setCholesterol(null);records.saveAndFlush(legacy);
        for(String locale:locales) {
            var form=mvc.perform(get("/health-record").param("lang",locale).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn();var html=Jsoup.parse(form.getResponse().getContentAsString());
            assertThat(html.select("form[data-health-record-form] input[name=_csrf]")).hasSize(1);
            assertThat(html.selectFirst("#activityLevel option").val()).isEmpty();
            var detail=mvc.perform(get("/health-record/{id}",saved.getId()).param("lang",locale).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn();html=Jsoup.parse(detail.getResponse().getContentAsString());
            assertThat(html.selectFirst("main").text()).contains("120 / 80","4.3","72.0","180.0","82.0","22.22","0.46","Synthetic condition <literal>")
                    .doesNotContain("??ui.health.");
            assertThat(html.select("main bdi[dir=ltr]")).hasSize(5);
            for(var measurement:html.select("main bdi[dir=ltr]")) assertThat(measurement.select("span").last().text()).isNotBlank();
            var missing=mvc.perform(get("/health-record/{id}",legacy.getId()).param("lang",locale).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn();html=Jsoup.parse(missing.getResponse().getContentAsString());
            assertThat(html.selectFirst("main").text()).contains("—").doesNotContain("null","NaN","Infinity");
        }
        mvc.perform(get("/health-record/{id}",saved.getId()).with(user(other.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/health-record/list").accept(org.springframework.http.MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/health-record/list").param("success","").param("lang","en").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Health record saved successfully!"))));
    }

    @Test void invalidHistoryFiltersKeepNamedEscapedDraftsAndPagingRetainsDateActivityAndSortInFourteenLocales() throws Exception {
        for(int i=0;i<7;i++) record(owner,day.atTime(9,30),"Moderately Active");
        for(String locale:locales) {
            var rejected=mvc.perform(get("/health-record/list").param("lang",locale).param("q","2026<literal>")
                    .param("from","bad-date<literal>").param("until",day.toString()).param("activity","Moderately Active").param("sort","oldest")
                    .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest()).andReturn();
            var html=Jsoup.parse(rejected.getResponse().getContentAsString());
            assertThat(html.selectFirst("#healthSearch").val()).isEqualTo("2026<literal>");
            assertThat(html.selectFirst("main").text()).contains("bad-date<literal>");
            assertThat(html.select("#health-history-errors a[href='#from']")).hasSize(1);
            assertThat(html.select(".health-history-card")).isEmpty();
            var valid=mvc.perform(get("/health-record/list").param("lang",locale).param("q","2026")
                    .param("from",day.toString()).param("until",day.toString()).param("activity","Moderately Active").param("sort","oldest")
                    .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andExpect(model().attribute("healthTotal",7L)).andReturn();
            html=Jsoup.parse(valid.getResponse().getContentAsString());assertThat(html.select(".health-history-card")).hasSize(6);
            assertThat(html.select("nav.connection-actions a").last().attr("href"))
                    .contains("q=2026","sort=oldest","from=2026-10-04","until=2026-10-04","activity=Moderately%20Active","page=2");
            assertThat(html.text()).doesNotContain("??ui.health.");
        }
        mvc.perform(get("/health-record/list").param("from",day.toString()).param("until",day.minusDays(1).toString())
                .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("healthHistoryFilter","until"));
        mvc.perform(get("/health-record/list").param("activity","Unavailable <choice>").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("healthHistoryFilter","activity"));
        mvc.perform(get("/health-record/list").param("page",Integer.MIN_VALUE+"").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("healthPage",1));
    }

    private MockHttpServletRequestBuilder validPost() {
        return validPost(java.util.Map.of());
    }
    private MockHttpServletRequestBuilder validPost(java.util.Map<String,String> changes) {
        var fields=new java.util.LinkedHashMap<String,String>();
        fields.put("baselineDate","2026-10-04T09:30");fields.put("systolicBloodPressure","120");fields.put("diastolicBloodPressure","80");
        fields.put("cholesterol","4.3");fields.put("weightKg","72");fields.put("heightCm","180");fields.put("waistCm","82");
        fields.put("activityLevel","Moderately Active");fields.putAll(changes);
        var request=post("/health-record").with(user(owner.getUsername()).roles("CLIENT")).with(csrf()).param("lang","en");
        fields.forEach(request::param);return request;
    }
    private User client() {
        String name="health-"+UUID.randomUUID();var user=new User(name+"@example.invalid","Local","Client",name,"test-password");
        user.setRole(Role.CLIENT);return users.saveAndFlush(user);
    }
    private HealthRecord record(User user,LocalDateTime date,String activity) {
        var record=new HealthRecord();record.setUser(user);record.setBaselineDate(date);record.setActivityLevel(activity);
        record.setSystolicBloodPressure(120);record.setDiastolicBloodPressure(80);record.setCholesterol(4.3);
        record.setWeightKg(72.0);record.setHeightCm(180.0);record.setWaistCm(82.0);return records.saveAndFlush(record);
    }
}
