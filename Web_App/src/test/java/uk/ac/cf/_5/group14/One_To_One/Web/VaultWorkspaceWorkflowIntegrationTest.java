package uk.ac.cf._5.group14.One_To_One.Web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Vault.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VaultWorkspaceWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired VaultNoteService service;
    @Autowired VaultNoteRepository notes;
    @Autowired JdbcTemplate jdbc;
    @Autowired uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository sessions;
    @MockBean VaultAiService ai;
    @MockBean PlatformSubscriptionService subscriptions;
    User owner, foreign;

    @BeforeEach void setup() {
        owner = account(); foreign = account();
        when(subscriptions.isPremium(eq(owner.getId()), any())).thenReturn(true);
        when(ai.isAvailable()).thenReturn(false);
    }

    @Test void stableBoundedLibraryLiteralSearchAndNativePageReturnRemainOwned() throws Exception {
        for (int i=1; i<=47; i++) service.create(owner.getId(), VaultNoteType.REFLECTION,
                i==1 ? "Saved 50%_effort! <reflection>" : "Reflection " + i, "Original reflection " + i,
                LocalDate.of(2026,10,1), null, "recovery", null);
        service.create(foreign.getId(), VaultNoteType.REFLECTION, "Private reflection", "Foreign content", null, null);
        notes.flush();
        jdbc.update("UPDATE vault_notes SET updated_at = TIMESTAMP '2026-10-01 10:00:00' WHERE user_id = ?", owner.getId());
        var first = service.searchPage(owner.getId(), null, null, false, null, null, 1);
        var last = service.searchPage(owner.getId(), null, null, false, null, null, Integer.MAX_VALUE);
        assertThat(first.total()).isEqualTo(47); assertThat(first.notes()).hasSize(20);
        assertThat(last.page()).isEqualTo(3); assertThat(last.notes()).hasSize(7);
        assertThat(first.notes()).extracting(VaultNote::getId).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(service.searchPage(owner.getId(), "%_", null, false, null, null, 1).total()).isEqualTo(1);
        assertThat(service.searchPage(owner.getId(), "!", null, false, null, null, 1).total()).isEqualTo(1);
        var page = page("/vault?page=2&type=REFLECTION&search=Reflection&from=2026-10-01&to=2026-10-01");
        assertThat(page.select(".vault-reflection-card")).hasSize(20);
        assertThat(page.selectFirst(".vault-pagination").text()).contains("Page 2 of 3", "47");
        String back = page.selectFirst("input[name=returnTo]").val();
        assertThat(back).contains("page=2", "type=REFLECTION", "from=2026-10-01", "search=Reflection");
        var note = first.notes().getFirst();
        mvc.perform(post("/vault/" + note.getId() + "/pin").with(user(owner.getUsername()).roles("CLIENT")).with(csrf()).param("returnTo", back))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(back));
        assertThat(page.select("input[name=noteIds][disabled]")).hasSize(20);
        mvc.perform(get("/vault").with(user(owner.getUsername()).roles("CLIENT")).param("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test void staleNativeDraftShowsSavedComparisonAndReviewedSavePreservesIdentityAndContext() throws Exception {
        var note = service.create(owner.getId(), VaultNoteType.TRAINING, "Original", "Original reflection", null, null);
        notes.flush(); String revision = note.getRevision();
        service.updateChecked(note.getId(), owner.getId(), VaultNoteType.GOAL, "Saved elsewhere", "Other editor's reflection",
                LocalDate.of(2026,10,2), null, "saved context", "GOOD", revision); notes.flush();
        var result=mvc.perform(post("/vault/"+note.getId()+"/edit").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("noteType","REFLECTION").param("title","Retained <draft>").param("content","Retained <reflection>")
                .param("linkedDate","2026-10-01").param("tags","my context").param("mood","")
                .param("revision",revision).param("returnTo","/vault?type=REFLECTION&page=2"))
                .andExpect(status().isConflict()).andReturn().getResponse();
        var html=Jsoup.parse(result.getContentAsString());
        assertThat(html.selectFirst("#reflectionTitle").val()).isEqualTo("Retained <draft>");
        assertThat(html.selectFirst("#reflectionContent").val()).isEqualTo("Retained <reflection>");
        assertThat(html.selectFirst("#vaultSavedContent").val()).isEqualTo("Other editor's reflection");
        assertThat(html.selectFirst(".vault-conflict").text()).contains("Saved elsewhere", "saved context", "Goal");
        String reviewed=html.selectFirst("input[name=revision]").val();
        assertThat(reviewed).isNotEqualTo(revision).matches("[a-f0-9]{64}");
        assertThat(notes.findById(note.getId()).orElseThrow().getTitle()).isEqualTo("Saved elsewhere");
        mvc.perform(post("/vault/"+note.getId()+"/edit").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("noteType","REFLECTION").param("title","Retained <draft>").param("content","Retained <reflection>")
                .param("revision",reviewed).param("returnTo","/vault?type=REFLECTION&page=2"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("vaultSaved",true));
        notes.flush(); assertThat(notes.findById(note.getId()).orElseThrow().getTitle()).isEqualTo("Retained <draft>");
    }

    @Test void insightCannotAttachToAReflectionChangedDuringGenerationAndMetadataInvalidatesOldInsight() throws Exception {
        var note=service.create(owner.getId(),VaultNoteType.TRAINING,"Source","Source reflection",null,null);
        service.saveAiSummaryChecked(note.getId(),owner.getId(),"Earlier insight",note.getRevision()); notes.flush();
        assertThat(note.getAiSourceRevision()).isEqualTo(note.getRevision());
        assertThat(note.getAiGeneratedAt()).isNotNull();
        service.updateChecked(note.getId(),owner.getId(),VaultNoteType.TRAINING,"Source","Source reflection",null,null,"","",note.getRevision());
        notes.flush();
        assertThat(note.getAiSummary()).isEqualTo("Earlier insight");
        assertThat(note.getAiSourceRevision()).isEqualTo(note.getRevision());
        when(ai.isAvailable()).thenReturn(true);
        when(ai.generateInsight(any())).thenAnswer(invocation->{
            service.updateChecked(note.getId(),owner.getId(),VaultNoteType.TRAINING,"Source","Source reflection",null,null,"changed tag",null,note.getRevision());
            notes.flush(); return "Obsolete generated reply";
        });
        mvc.perform(post("/vault/ai/insight/"+note.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("aiConsent","true"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("vaultAiError","stale"));
        assertThat(notes.findById(note.getId()).orElseThrow().getAiSummary()).isNull();
        assertThat(notes.findById(note.getId()).orElseThrow().getAiGeneratedAt()).isNull();
        assertThat(notes.findById(note.getId()).orElseThrow().getAiSourceRevision()).isNull();
        assertThat(notes.findById(note.getId()).orElseThrow().getTags()).isEqualTo("changed tag");
        verify(ai).generateInsight(any());
    }

    @Test void threeNativeViewsRenderOnceAcrossAllLocalesWithoutMutationAndDisabledAiIsHonest() throws Exception {
        var linked=new uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession();
        linked.setUser(owner); linked.setDate(LocalDate.of(2026,10,1));
        linked.setCreatedAt(java.time.LocalDateTime.now()); linked.setNameSnapshot("Owned training session");
        linked=sessions.saveAndFlush(linked);
        var note=service.create(owner.getId(),VaultNoteType.INJURY,"Literal <reflection>","Literal <script>reflection</script>",LocalDate.of(2026,10,1),linked.getId(),"recovery","GREAT");
        service.saveAiSummaryChecked(note.getId(),owner.getId(),"Saved insight <literal text>",note.getRevision());
        notes.flush(); long count=notes.count();
        for(String language:List.of("en","cy","es","fr","de","it","pt","pl","nl","zh","ja","ko","ar","hi")) {
            for(String path:List.of("/vault","/vault/"+note.getId(),"/vault/"+note.getId()+"/edit")) {
                var html=page(path+"?lang="+language);
                assertThat(html.select("h1")).hasSize(1);
                assertThat(html.select("main main")).isEmpty();
                assertThat(html.select("main").text()).doesNotContain("??","{0}","{1}","{2}");
                assertThat(html.select("script[src*=/js/notes/vault.js]")).hasSize(1);
                assertThat(html.selectFirst("script[src*=/js/notes/vault.js]").attr("src"))
                        .endsWith("?v="+new uk.ac.cf._5.group14.One_To_One.Config.UiStyleBundleAdvice().uiCssVersion());
                if(path.endsWith("/edit")) {
                    assertThat(html.selectFirst("input[name=revision]").val()).matches("[a-f0-9]{64}");
                    assertThat(html.select("#reflectionSession option[selected]").text()).contains("Owned training session");
                }
                else {
                    assertThat(html.select(".vault-ai-controls fieldset[disabled]")).hasSize(1);
                    assertThat(html.select(".vault-reflection-text script")).isEmpty();
                }
            }
        }
        assertThat(notes.count()).isEqualTo(count);
        verify(ai,never()).generateInsight(any()); verify(ai,never()).summariseWeek(any()); verify(ai,never()).rewriteCheckin(any());
        var other=service.create(foreign.getId(),VaultNoteType.TRAINING,"Private","Secret",null,null); notes.flush();
        for(String path:List.of("/vault/"+other.getId(),"/vault/"+other.getId()+"/edit"))
            mvc.perform(get(path).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mvc.perform(get(URI.create(path)).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private User account() {
        String name="vault-"+UUID.randomUUID();
        var person=new User(name+"@example.invalid","Local","Vault",name,"test-password");
        person.setRole(Role.CLIENT); return users.saveAndFlush(person);
    }
}
