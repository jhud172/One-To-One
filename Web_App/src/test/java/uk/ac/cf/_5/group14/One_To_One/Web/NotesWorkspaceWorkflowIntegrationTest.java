package uk.ac.cf._5.group14.One_To_One.Web;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Notes.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotesWorkspaceWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired NoteService service;
    @Autowired NoteFolderService folders;
    @Autowired NoteRepository notes;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    User owner, foreign;
    NoteFolder folder;

    @BeforeEach void setup() {
        owner = account(); foreign = account();
        folder = folders.createFolder(owner, "Training <reflection>", "orange");
    }

    @Test void nativeAndApiPagesAreBoundedStablePrivateAndSearchTreatsWildcardsLiterally() throws Exception {
        for (int i = 0; i < 47; i++) service.create(owner, folder.getId(), i == 0 ? "50%_effort!" : "Training reflection " + i, "<p>Original coaching context</p>", "orange");
        var otherFolder = folders.createFolder(foreign, "Private folder", "slate");
        service.create(foreign, otherFolder.getId(), "Private training reflection", "Private", "slate");
        notes.flush();
        jdbc.update("update notes set updated_at=? where user_id=?", java.sql.Timestamp.valueOf("2026-10-01 10:00:00"), owner.getId());
        entities.clear();
        var first = service.searchPage(owner, folder.getId(), null, 1);
        var second = service.searchPage(owner, folder.getId(), null, 2);
        var last = service.searchPage(owner, folder.getId(), null, Integer.MAX_VALUE);
        assertThat(first.getTotalElements()).isEqualTo(47);
        assertThat(first.getContent()).hasSize(20);
        assertThat(second.getContent()).hasSize(20);
        assertThat(last.getContent()).hasSize(7);
        assertThat(last.getNumber()).isEqualTo(2);
        var expected = notes.findByUserOrderByUpdatedAtDesc(owner).stream().map(Note::getId).sorted(java.util.Comparator.reverseOrder()).toList();
        assertThat(java.util.stream.Stream.of(first, second, last).flatMap(p -> p.getContent().stream()).map(Note::getId).toList()).containsExactlyElementsOf(expected);
        assertThat(service.searchPage(owner, folder.getId(), "%_", 1).getContent()).extracting(Note::getTitle).containsExactly("50%_effort!");
        assertThat(service.search(owner, folder.getId(), "%_")).hasSize(1);
        var html = page("/notes?folderId=" + folder.getId() + "&page=2");
        assertThat(html.select("#noteList [data-note-id]")).hasSize(20);
        assertThat(html.select("#notesPagination a")).hasSize(2);
        assertThat(html.select("#notesPagination").text()).contains("2", "3", "47");
        assertThat(page("/notes/folders/" + folder.getId() + "?page=999999999").select("a[href^=/notes/]:has(.notes-rich-content)")).hasSize(7);
        mvc.perform(get("/notes/api/notes/page").param("folderId", folder.getId().toString()).param("page", "3").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes.length()").value(7)).andExpect(jsonPath("$.total").value(47)).andExpect(jsonPath("$.pageCount").value(3));
        mvc.perform(get("/notes/api/notes").param("folderId", folder.getId().toString()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(47));
    }

    @Test void staleNativeDraftIsRetainedWithCurrentContentAndReviewedSaveKeepsIdentity() throws Exception {
        var note = service.create(owner, folder.getId(), "Original", "<p>Original <strong>training</strong></p>", "orange"); notes.flush();
        String originalRevision = service.getNoteForUser(owner, note.getId()).getRevision();
        service.updateChecked(owner, note.getId(), "Saved elsewhere", "<p>Other editor</p>", folder.getId(), "red", originalRevision); notes.flush();
        var response = mvc.perform(post("/notes/" + note.getId() + "/edit").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("title", "Retained <draft>").param("content", "<p>My <em>draft</em></p>").param("folderId", folder.getId().toString())
                .param("noteColour", "orange").param("revision", originalRevision))
                .andExpect(status().isConflict()).andReturn().getResponse();
        var html = Jsoup.parse(response.getContentAsString());
        assertThat(html.selectFirst("#noteTitle").val()).isEqualTo("Retained <draft>");
        assertThat(html.select("[role=alert]").text()).contains("Saved elsewhere", "Other editor");
        assertThat(html.selectFirst("#editor em").text()).isEqualTo("draft");
        String reviewed = html.selectFirst("input[name=revision]").val();
        assertThat(reviewed).isNotEqualTo(originalRevision).matches("[a-f0-9]{64}");
        assertThat(service.getNoteForUser(owner, note.getId()).getTitle()).isEqualTo("Saved elsewhere");
        mvc.perform(post("/notes/" + note.getId() + "/edit").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("title", "Retained <draft>").param("content", "<p>My <em>draft</em></p>").param("folderId", folder.getId().toString())
                .param("noteColour", "orange").param("revision", reviewed))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/notes/" + note.getId()));
        notes.flush();
        assertThat(service.getNoteForUser(owner, note.getId()).getTitle()).isEqualTo("Retained <draft>");
        assertThat(notes.count()).isGreaterThanOrEqualTo(1);
    }

    @Test void apiConflictAndValidationCannotOverwriteOrMoveNotesAndExportsRemainSafe() throws Exception {
        var note = service.create(owner, folder.getId(), "Safe <title>", "<p>Safe <strong>content</strong><script>bad()</script><a href=\"javascript:bad()\">link</a></p>", "orange"); notes.flush();
        var foreignFolder = folders.createFolder(foreign, "Other owner", "red");
        var privateNote = service.create(foreign, foreignFolder.getId(), "Private note", "Secret", "red"); notes.flush();
        String originalRevision = service.getNoteForUser(owner, note.getId()).getRevision();
        var api = get("/notes/api/notes/" + note.getId()).with(user(owner.getUsername()).roles("CLIENT"));
        mvc.perform(api).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(originalRevision))
                .andExpect(jsonPath("$.plainContent").value(org.hamcrest.Matchers.containsString("Safe content")));
        service.updateChecked(owner, note.getId(), "New saved title", "<p>New safe content</p>", folder.getId(), "red", originalRevision); notes.flush();
        mvc.perform(post("/notes/api/notes/" + note.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Stale title\",\"content\":\"Stale\",\"revision\":\"" + originalRevision + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("STALE_NOTE"));
        String current = service.getNoteForUser(owner, note.getId()).getRevision();
        mvc.perform(post("/notes/api/notes/" + note.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Bad move\",\"content\":\"Lost\",\"revision\":\"" + current + "\",\"folderId\":" + foreignFolder.getId() + "}"))
                .andExpect(status().isNotFound());
        for (String path : List.of("/notes/" + privateNote.getId(), "/notes/" + privateNote.getId() + "/edit", "/notes/export/" + privateNote.getId(), "/notes/api/notes/" + privateNote.getId(), "/notes?folderId=" + foreignFolder.getId(), "/notes/folders/" + foreignFolder.getId(), "/notes/api/notes/page?folderId=" + foreignFolder.getId())) {
            mvc.perform(get(URI.create(path)).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/notes/api/notes/page").param("page", "0").with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest());
        mvc.perform(get("/notes").param("folderId", folder.getId().toString()).param("page", "bad").with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest());
        assertThat(service.getNoteForUser(owner, note.getId()).getTitle()).isEqualTo("New saved title");
        assertThat(service.getNoteForUser(owner, note.getId()).getFolder().getId()).isEqualTo(folder.getId());
        mvc.perform(get("/notes/export/" + note.getId()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New safe content")));
    }

    @Test void fourRealViewsRenderOnceInEveryLocaleWithSavedRevisionAndNativeFallback() throws Exception {
        var note = service.create(owner, folder.getId(), "Local <reflection>", "<p>Training <strong>context</strong></p>", "orange"); notes.flush();
        long count = notes.count();
        for (String language : List.of("en", "cy", "es", "fr", "de", "it", "pt", "pl", "nl", "zh", "ja", "ko", "ar", "hi")) {
            for (String path : List.of("/notes?folderId=" + folder.getId() + "&noteId=" + note.getId(), "/notes/folders/" + folder.getId(), "/notes/" + note.getId(), "/notes/" + note.getId() + "/edit")) {
                var html = page(path + (path.contains("?") ? "&" : "?") + "lang=" + language);
                assertThat(html.select("h1")).hasSize(1);
                assertThat(html.select("main").text()).doesNotContain("??", "{0}", "{1}", "{2}");
                assertThat(html.select("main main")).isEmpty();
                if (path.startsWith("/notes?")) {
                    assertThat(html.select("script[src*=/js/notes/notes.js]")).hasSize(1);
                    assertThat(html.selectFirst("script[src*=/js/notes/notes.js]").attr("src")).endsWith("?v=" + new uk.ac.cf._5.group14.One_To_One.Config.UiStyleBundleAdvice().uiCssVersion());
                    assertThat(html.select("#notesCollections[open],#notesListCollection[open]")).hasSize(2);
                    assertThat(html.select("#notesRichEditor[hidden],#notesRichFeedback[hidden]")).hasSize(2);
                    assertThat(html.selectFirst("#noteTitle").hasAttr("readonly")).isTrue();
                    assertThat(html.selectFirst("#notes-app").attr("data-active-revision")).matches("[a-f0-9]{64}");
                    assertThat(html.selectFirst("#notesPagination")).isNotNull();
                }
                if (path.endsWith("/edit")) {
                    assertThat(html.select("script[src*=/js/notes/note-editor.js]")).hasSize(1);
                    assertThat(html.selectFirst("input[name=revision]").val()).matches("[a-f0-9]{64}");
                    assertThat(html.selectFirst("#plainContent").text()).contains("Training");
                }
                if (path.startsWith("/notes/folders/")) {
                    assertThat(html.select("#notesFolderControls[open]")).hasSize(1);
                    assertThat(html.select("script[src*=/js/notes/notes-navigation.js]")).hasSize(1);
                }
            }
        }
        assertThat(notes.count()).isEqualTo(count);
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mvc.perform(get(URI.create(path)).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private User account() {
        String name = "notes-" + UUID.randomUUID();
        var user = new User(name + "@example.invalid", "Local", "Notes", name, "test-password");
        user.setRole(Role.CLIENT); return users.saveAndFlush(user);
    }
}
