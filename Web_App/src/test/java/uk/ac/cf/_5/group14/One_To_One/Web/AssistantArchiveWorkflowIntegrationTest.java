package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
import uk.ac.cf._5.group14.One_To_One.ChatV2.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AssistantArchiveWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ChatV2ThreadService archive;
    @Autowired ChatThreadRepository threads;
    @Autowired ChatMessageRepository messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    User owner, foreign;

    @BeforeEach void setup() {
        owner = account();
        foreign = account();
    }

    @Test void longOwnedHistoryIsBoundedAndStableAndFolderSearchTreatsPercentLiterally() throws Exception {
        var folder = archive.createFolder(owner, "Earlier <collection>", "#0f172a", "chat");
        List<ChatThread> records = new ArrayList<>();
        for (int i = 0; i < 47; i++) {
            var item = archive.createThread(owner, i < 25 ? folder : null);
            archive.updateThreadSettings(item, i == 0 ? "Archive <50%>" : "Archive record " + i,
                    null, null, i == 0, i % 2 == 0, null);
            records.add(item);
        }
        var privateThread = archive.createThread(foreign, null);
        archive.updateThreadSettings(privateThread, "Foreign private history", null, null, true, true, null);
        threads.flush();
        jdbc.update("update chat_threads set updated_at=? where user_id=?", java.sql.Timestamp.from(Instant.parse("2026-10-01T10:00:00Z")), owner.getId());
        entities.clear();
        var expected = records.stream().sorted(Comparator.comparing(ChatThread::isPinned).reversed()
                .thenComparing(ChatThread::getId, Comparator.reverseOrder())).map(ChatThread::getId).toList();
        var first = archive.historyPage(owner, null, "", 1);
        var second = archive.historyPage(owner, null, "", 2);
        var last = archive.historyPage(owner, null, "", Integer.MAX_VALUE);
        assertThat(first.getTotalElements()).isEqualTo(47);
        assertThat(first.getContent()).extracting(ChatThread::getId).containsExactlyElementsOf(expected.subList(0, 20));
        assertThat(second.getContent()).extracting(ChatThread::getId).containsExactlyElementsOf(expected.subList(20, 40));
        assertThat(last.getContent()).extracting(ChatThread::getId).containsExactlyElementsOf(expected.subList(40, 47));
        assertThat(archive.historyPage(owner, folder, "", 2).getTotalElements()).isEqualTo(25);
        assertThat(archive.historyPage(owner, folder, "50%", 1).getContent()).extracting(ChatThread::getTitle).containsExactly("Archive <50%>");
        var html = page("/chatv2/history?page=2&lang=en");
        assertThat(html.select("[data-history-thread]")).hasSize(20);
        assertThat(html.select(".assistant-history-pagination").text()).contains("2", "3", "47");
        assertThat(html.text()).doesNotContain("Foreign private history");
        var filtered = page("/chatv2/history/folder/" + folder.getId() + "?q=50%25&lang=en");
        assertThat(filtered.select("[data-history-thread]")).hasSize(1);
        assertThat(filtered.selectFirst("h1").text()).isEqualTo("Earlier <collection>");
        assertThat(filtered.selectFirst(".assistant-history-folders a[aria-current=page]").text()).isEqualTo("Earlier <collection>");
        assertThat(filtered.selectFirst("[data-history-thread] a").attr("href")).contains("collection=" + folder.getId(), "returnPage=1", "q=50%25");
    }

    @Test void messageHistoryStartsAtLatestPageAndPreservesLiteralRolesInstructionsAndReturnContext() throws Exception {
        var folder = archive.createFolder(owner, "Historic collection", "#0f172a", "chat");
        var thread = archive.createThread(owner, folder);
        thread.setType(ChatType.TRAINER_CLIENT);
        archive.updateThreadSettings(thread, "Earlier <coaching>", null, null, true, true, "Original <instructions>\nSecond line");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 65; i++) {
            var message = archive.appendMessage(thread, i == 64 ? ChatMessageRole.SYSTEM : i % 2 == 0 ? ChatMessageRole.USER : ChatMessageRole.ASSISTANT,
                    "Saved <message> " + i + "\nLiteral second line");
            ids.add(message.getId());
        }
        messages.flush();
        jdbc.update("update chat_messages set created_at=? where thread_id=?", java.sql.Timestamp.from(Instant.parse("2026-10-01T10:00:00Z")), thread.getId());
        entities.clear();
        var html = page("/chatv2/history/thread/" + thread.getId() + "?q=Saved&returnPage=2&lang=en");
        assertThat(html.select("[data-history-message]")).hasSize(5);
        assertThat(html.select("[data-history-message]").eachAttr("data-history-message")).containsExactlyElementsOf(ids.subList(60, 65).stream().map(Object::toString).toList());
        assertThat(html.select("h1")).hasSize(1);
        assertThat(html.selectFirst("h1").text()).isEqualTo("Earlier <coaching>");
        assertThat(html.select(".assistant-history-messages").text()).contains("Saved reply", "Saved system message").doesNotContain("Charlie");
        assertThat(html.selectFirst("details.assistant-history-instructions p").wholeText()).isEqualTo("Original <instructions>\nSecond line");
        assertThat(html.selectFirst("section.assistant-history-panel > a").attr("href")).isEqualTo("/chatv2/history?q=Saved&page=2");
        assertThat(html.select("time")).allSatisfy(time -> {
            assertThat(time.attr("datetime")).startsWith("2026-10-01T10:00:00");
            assertThat(time.text()).doesNotContain("T10:");
            assertThat(time.attr("dir")).isEqualTo("ltr");
        });
        var folderView = page("/chatv2/history/thread/" + thread.getId() + "?page=2&q=Saved&returnPage=2&collection=" + folder.getId() + "&lang=en");
        assertThat(folderView.select("[data-history-message]")).hasSize(30);
        assertThat(folderView.selectFirst("section.assistant-history-panel > a").attr("href")).isEqualTo("/chatv2/history/folder/" + folder.getId() + "?q=Saved&page=2");
        assertThat(folderView.selectFirst("a[rel=prev]").attr("href")).contains("page=1", "q=Saved", "returnPage=2", "collection=" + folder.getId());
        assertThat(archive.historyMessagesPage(thread, Integer.MAX_VALUE).getNumber()).isEqualTo(2);
        assertThat(messages.countByThread(thread)).isEqualTo(65);
    }

    @Test void foreignHistoryAndReturnCollectionsStayPrivateAndInvalidPagesAreControlled() throws Exception {
        var folder = archive.createFolder(foreign, "Private collection", "#0f172a", "chat");
        var privateThread = archive.createThread(foreign, folder);
        var own = archive.createThread(owner, null);
        for (String path : List.of("/chatv2/history/folder/" + folder.getId(), "/chatv2/history/thread/" + privateThread.getId(),
                "/chatv2/history/thread/" + own.getId() + "?collection=" + folder.getId())) {
            mvc.perform(get(path).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        }
        for (String path : List.of("/chatv2/history?page=0", "/chatv2/history?page=-1", "/chatv2/history?page=bad",
                "/chatv2/history/thread/" + own.getId() + "?page=0", "/chatv2/history/thread/" + own.getId() + "?returnPage=0")) {
            mvc.perform(get(path).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest());
        }
        assertThat(page("/chatv2/history?lang=en").text()).doesNotContain("Private collection");
        mvc.perform(get("/chatv2/" + privateThread.getId()).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isNotFound());
        mvc.perform(get("/chatv2/" + own.getId()).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(redirectedUrl("/chat"));
    }

    @Test void realArchivePagesRenderAllFourteenLocalesWithoutActiveAssistantScriptsOrMutations() throws Exception {
        var folder = archive.createFolder(owner, "Collection <literal>", "#0f172a", "chat");
        var thread = archive.createThread(owner, folder);
        archive.updateThreadSettings(thread, "Conversation <literal>", null, null, true, true, "Saved <instructions>");
        archive.appendMessage(thread, ChatMessageRole.USER, "Owned <literal>\nSecond line");
        for (String language : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            for (String path : List.of("/chatv2/history", "/chatv2/history/folder/" + folder.getId(), "/chatv2/history/thread/" + thread.getId())) {
                var html = page(path + "?lang=" + language);
                assertThat(html.select("h1")).hasSize(1);
                assertThat(html.select("main").text()).doesNotContain("??", "{0}", "{1}", "{2}");
                assertThat(html.select("script[src*=chat-v2.js]")).isEmpty();
                assertThat(html.select("script[src*=archive-history.js]")).hasSize(1);
                assertThat(html.selectFirst("script[src*=archive-history.js]").attr("src"))
                        .endsWith("?v=" + new uk.ac.cf._5.group14.One_To_One.Config.UiStyleBundleAdvice().uiCssVersion());
                assertThat(html.select(".assistant-history-v2 form[method=post]")).isEmpty();
                assertThat(html.select(".assistant-history-pagination")).hasSize(1);
            }
        }
        assertThat(messages.countByThread(thread)).isEqualTo(1);
        assertThat(threads.findById(thread.getId()).orElseThrow().getCustomInstructions()).isEqualTo("Saved <instructions>");
        var empty = page("/chatv2/history?q=Missing&lang=en");
        assertThat(empty.select("[data-history-thread]")).isEmpty();
        assertThat(empty.select(".assistant-history-pagination").text()).contains("1", "0");
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mvc.perform(get(java.net.URI.create(path)).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private User account() {
        String name = "archive-" + UUID.randomUUID();
        var account = new User(name + "@example.invalid", "Local", "Archive", name, "test-password");
        account.setRole(Role.CLIENT);
        return users.saveAndFlush(account);
    }
}
