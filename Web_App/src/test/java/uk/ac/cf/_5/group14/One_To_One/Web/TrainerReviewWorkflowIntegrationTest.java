package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Reviews.*;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerReviewWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerClientLinkRepository links;
    @Autowired TrainerReviewRepository reviews;
    @Autowired TrainerReviewService service;
    User client, trainer;
    TrainerClientLink link;

    @BeforeEach void setup() {
        client = account(Role.CLIENT); trainer = account(Role.TRAINER);
        link = new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE);
        link.setActivatedAt(Instant.now()); link=links.saveAndFlush(link);
    }

    @Test void threeViewsInFourteenLocalesExposeOneNativeReviewAndEligibleEntry() throws Exception {
        var reviewer=account(Role.CLIENT);
        var historicalLink=links.saveAndFlush(new TrainerClientLink(reviewer.getId(),trainer.getId(),TrainerClientLinkStatus.ACTIVE));
        reviews.saveAndFlush(new TrainerReview(trainer.getId(),reviewer.getId(),historicalLink.getId(),5,
                "Professional,Patient,Legacy <tag>","Public English <review>"));
        for (String locale : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            var form=Jsoup.parse(mvc.perform(get("/trainers/{id}/review",trainer.getId()).param("lang",locale)
                    .with(user(client.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(form.select("h1")).hasSize(1);
            assertThat(form.select("form[data-review-form]")).hasSize(1);
            assertThat(form.select("input[type=radio][name=stars][required]")).hasSize(5);
            assertThat(form.select("input[type=checkbox][name=tags]")).hasSize(6);
            assertThat(form.select("script[src*='review-form-page.js']")).hasSize(1);
            assertThat(form.selectFirst("textarea[name=comment]").attr("dir")).isEqualTo("auto");
            assertThat(form.select("form[data-review-form] input[name=_csrf]")).hasSize(1);
            assertThat(form.text()).doesNotContain("??ui.","Private coach notes");
            var vanity=Jsoup.parse(mvc.perform(get("/u/{name}",trainer.getUsername()).param("lang",locale)
                    .with(user(client.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(vanity.select("a[href='/trainers/"+trainer.getId()+"/review']")).hasSize(1);
            assertThat(vanity.select("a[href='/signup']").stream().anyMatch(a->a.hasClass("guest-action--primary"))).isFalse();
            assertThat(vanity.select("[dir=auto]").text()).contains("Public English <review>","Legacy <tag>");
            assertThat(vanity.select("tag, review")).isEmpty();
            assertThat(vanity.text()).doesNotContain("??ui.");
            var profile=Jsoup.parse(mvc.perform(get("/trainers/{id}/profile",trainer.getId()).param("lang",locale)
                    .with(user(client.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(profile.select("a[href='/trainers/"+trainer.getId()+"/review']")).hasSize(1);
            assertThat(profile.select(".plan-coaching-note[dir=auto]").text()).isEqualTo("Public English <review>");
            assertThat(profile.select(".portfolio-specialities [dir=auto]").text()).contains("Legacy <tag>");
            assertThat(profile.text()).doesNotContain("??ui.");
            if (locale.equals("ar")) assertThat(profile.select(".portfolio-specialities [dir=auto]").text()).contains("صبور");
        }
    }

    @Test void invalidRatingTagsAndLongCommentKeepDraftAndNamedFieldDestinations() throws Exception {
        String comment="Keep <literal draft> "+"x".repeat(10000);
        long before=reviews.count();
        var invalid=mvc.perform(post("/trainers/{id}/review",trainer.getId()).with(user(client.getUsername()).roles("CLIENT"))
                .with(csrf()).param("stars","7").param("tags","Patient","Unknown tag").param("comment",comment))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("reviewError",true)).andReturn();
        assertThat(invalid.getFlashMap().get("reviewFieldErrors")).isEqualTo(List.of("stars","comment","tags"));
        var retained=Jsoup.parse(mvc.perform(get("/trainers/{id}/review",trainer.getId())
                .with(user(client.getUsername()).roles("CLIENT")).cookie(invalid.getResponse().getCookies()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(retained.selectFirst("#reviewComment").val()).isEqualTo(comment);
        assertThat(retained.select("#reviewError a").eachAttr("href")).containsExactly("#reviewRating1","#reviewTags","#reviewComment");
        assertThat(retained.select("input[value=Patient][checked]")).hasSize(1);
        assertThat(retained.selectFirst("#reviewComment").attr("aria-invalid")).isEqualTo("true");
        assertThat(retained.selectFirst("form[data-review-form]").attr("data-retained")).isEqualTo("true");
        assertThat(retained.select("literal")).isEmpty();
        assertThat(reviews.count()).isEqualTo(before);
        var valid=mvc.perform(post("/trainers/{id}/review",trainer.getId()).with(user(client.getUsername()).roles("CLIENT"))
                .with(csrf()).param("stars","4").param("tags","Patient","Professional","Patient").param("comment","Good <coaching>"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("reviewSaved",true)).andReturn();
        var saved=reviews.findByTrainerIdAndClientIdAndLinkId(trainer.getId(),client.getId(),link.getId()).orElseThrow();
        assertThat(saved.getTags()).isEqualTo("Patient,Professional");
        var profile=Jsoup.parse(mvc.perform(get("/trainers/{id}/profile",trainer.getId()).with(user(client.getUsername()).roles("CLIENT"))
                .cookie(valid.getResponse().getCookies())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(profile.select("[role=status]").text()).contains("Review submitted.");
        assertThat(profile.text()).contains("Good <coaching>"); assertThat(profile.select("coaching")).isEmpty();
    }

    @Test void repeatedSubmissionAndLostEligibilityRetainDraftWithoutSecondReview() throws Exception {
        var saved=service.createReview(client.getId(),trainer.getId(),5,"Patient","Original public review");
        long before=reviews.count();
        var repeat=Jsoup.parse(mvc.perform(post("/trainers/{id}/review",trainer.getId()).with(user(client.getUsername()).roles("CLIENT"))
                .with(csrf()).param("stars","3").param("tags","Professional").param("comment","Second <retained draft>"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString());
        assertThat(repeat.selectFirst("#reviewComment").val()).isEqualTo("Second <retained draft>");
        assertThat(repeat.select("form[data-review-form] button[type=submit][disabled]")).hasSize(1);
        assertThat(repeat.select("[role=alert]").text()).contains("already reviewed");
        assertThat(reviews.count()).isEqualTo(before);
        assertThat(reviews.findById(saved.getId()).orElseThrow().getComment()).isEqualTo("Original public review");
        reviews.delete(saved); reviews.flush();
        link.setStatus(TrainerClientLinkStatus.PAUSED); links.saveAndFlush(link);
        var paused=Jsoup.parse(mvc.perform(post("/trainers/{id}/review",trainer.getId()).with(user(client.getUsername()).roles("CLIENT"))
                .with(csrf()).param("stars","4").param("comment","Keep after relationship changed"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString());
        assertThat(paused.selectFirst("#reviewComment").val()).isEqualTo("Keep after relationship changed");
        assertThat(paused.select("form[data-review-form] button[type=submit][disabled]")).hasSize(1);
        assertThat(service.canClientReviewTrainer(client.getId(),trainer.getId())).isFalse();
        assertThat(reviews.findByTrainerIdAndClientIdAndLinkId(trainer.getId(),client.getId(),link.getId())).isEmpty();
    }

    @Test void missingNonTrainerUnverifiedAndDisabledRoutesStay404AndRolesAndCsrfStayProtected() throws Exception {
        for (Long missing : List.of(Long.MAX_VALUE,client.getId())) {
            mvc.perform(get("/trainers/{id}/review",missing).with(user(client.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
            mvc.perform(post("/trainers/{id}/review",missing).with(user(client.getUsername()).roles("CLIENT"))
                    .with(csrf()).param("stars","5")).andExpect(status().isNotFound());
        }
        trainer.setTrainerVerified(false); users.saveAndFlush(trainer);
        mvc.perform(get("/trainers/{id}/review",trainer.getId()).with(user(client.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        assertThat(service.canClientReviewTrainer(client.getId(),trainer.getId())).isFalse();
        trainer.setTrainerVerified(true); trainer.setEnabled(false); users.saveAndFlush(trainer);
        mvc.perform(post("/trainers/{id}/review",trainer.getId()).with(user(client.getUsername()).roles("CLIENT"))
                .with(csrf()).param("stars","5")).andExpect(status().isNotFound());
        mvc.perform(get("/trainers/{id}/review",trainer.getId()).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post("/trainers/{id}/review",trainer.getId()).with(user(client.getUsername()).roles("CLIENT"))
                .param("stars","5")).andExpect(status().isUnauthorized());
        assertThat(reviews.findByTrainerIdAndClientIdAndLinkId(trainer.getId(),client.getId(),link.getId())).isEmpty();
    }

    private User account(Role role) {
        var result=new User(); String name="review"+UUID.randomUUID().toString().replace("-","");
        result.setUsername(name); result.setEmail(name+"@example.test"); result.setPassword("local-test-only");
        result.setFirstName(role==Role.TRAINER?"Jordan":"Avery"); result.setLastName("<local>");
        result.setRole(role); result.setTrainerVerified(role==Role.TRAINER); return users.saveAndFlush(result);
    }
}
