package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Inbox.InboxService;
import uk.ac.cf._5.group14.One_To_One.Messaging.*;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class InboxWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerClientLinkRepository links;
    @Autowired MessageThreadRepository threads;
    @Autowired ThreadMessageRepository messages;
    @Autowired MessageReadStateRepository reads;
    @Autowired MessagingService messaging;
    @Autowired InboxService inbox;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired uk.ac.cf._5.group14.One_To_One.Notifications.NotificationRepository notifications;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    User owner, coach, foreign;
    TrainerClientLink link;
    MessageThread thread;
    @BeforeEach void setup() {
        owner=account(Role.CLIENT);coach=account(Role.TRAINER);foreign=account(Role.CLIENT);
        link=links.saveAndFlush(new TrainerClientLink(owner.getId(),coach.getId(),TrainerClientLinkStatus.ACTIVE));
        thread=messaging.ensureThreadForLink(link);threads.flush();
    }

    @Test void sendAcknowledgesItsOwnSavedMessageAndHistoryAndPreviewsHaveStableTimestampTies() throws Exception {
        var future=new Message(thread,coach.getId(),MessageType.TEXT,"Historical future clock");
        org.springframework.test.util.ReflectionTestUtils.setField(future,"createdAt",Instant.now().plusSeconds(86400));
        messages.saveAndFlush(future);
        var response=mvc.perform(post("/api/inbox/threads/{id}/send",thread.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .contentType("application/json").content("{\"bodyText\":\"Actual owned reply <literal>\"}"))
                .andExpect(status().isOk()).andReturn();
        long acknowledged=json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        var saved=messages.findById(acknowledged).orElseThrow();
        assertThat(saved.getBodyText()).isEqualTo("Actual owned reply <literal>");assertThat(saved.getSenderUserId()).isEqualTo(owner.getId());
        assertThat(saved.getId()).isNotEqualTo(future.getId());
        assertThat(notifications.findByUserOrderByCreatedAtDesc(coach, org.springframework.data.domain.PageRequest.of(0,50)))
                .extracting(uk.ac.cf._5.group14.One_To_One.Notifications.Notification::getCtaUrl).contains("/inbox/"+thread.getId());
        Instant tie=Instant.now().plusSeconds(172800);var first=record(coach,"First tied",tie);var second=record(coach,"Second tied",tie);
        assertThat(inbox.getMessages(owner,thread.getId())).extracting(Message::getId).endsWith(first.getId(),second.getId());
        assertThat(inbox.listConversations(owner).getFirst().getLastMessageSnippet()).isEqualTo("Second tied");
    }

    @Test void readPositionMarksOnlyDisplayedSnapshotAndRepeatedReadIsIdempotentAndPrivate() throws Exception {
        var first=record(coach,"First received",Instant.now());var second=record(coach,"Later received",Instant.now());
        mvc.perform(post("/api/inbox/threads/{id}/read",thread.getId()).param("upToId",first.getId().toString())
                .with(user(owner.getUsername()).roles("CLIENT")).with(csrf())).andExpect(status().isOk());
        inbox.markRead(owner,thread.getId(),first.getId());
        assertThat(reads.findByUserIdAndMessageIdIn(owner.getId(),java.util.List.of(first.getId(),second.getId())))
                .extracting(MessageReadState::getMessageId).containsExactly(first.getId());
        assertThat(inbox.listConversations(owner).getFirst().getUnreadCount()).isEqualTo(1);
        mvc.perform(get("/api/inbox/threads/{id}",thread.getId()).with(user(foreign.getUsername()).roles("CLIENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/inbox/threads/{id}/read",thread.getId()).with(user(foreign.getUsername()).roles("CLIENT")).with(csrf())).andExpect(status().isForbidden());
        assertThat(reads.findByUserIdAndMessageIdIn(foreign.getId(),java.util.List.of(first.getId(),second.getId()))).isEmpty();
        mvc.perform(get("/inbox/{id}",thread.getId()).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk());
        assertThat(inbox.listConversations(owner).getFirst().getUnreadCount()).isZero();
    }

    @Test void currentRelationshipIsRefreshedBeforeSendAndNativeReceiptAttachmentsAndDetailsRenderInFourteenLocales() throws Exception {
        var own=messaging.sendMessage(thread.getId(),owner.getId(),MessageType.TEXT,"Owned <body>","Exercise <demo>","https://example.invalid/demo","link");
        messages.flush();inbox.markRead(coach,thread.getId());
        for(String locale:new String[]{"en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh"}) {
            var result=mvc.perform(get("/inbox/{id}",thread.getId()).param("lang",locale).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn();
            var html=Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(html.select("main h1")).hasSize(1);assertThat(html.selectFirst("main h1").text()).isEqualTo(coach.getFullName());
            assertThat(html.select("script[src^='/js/messaging/inbox.js']")).hasSize(1);
            assertThat(html.selectFirst("#inboxMessages .inbox-attachment").attr("href")).isEqualTo("https://example.invalid/demo");
            assertThat(html.selectFirst("#inboxMessages .inbox-attachment").text()).isEqualTo("Exercise <demo>");
            assertThat(html.selectFirst("#inboxMessages .inbox-receipt")).isNotNull();
            assertThat(html.selectFirst("#inboxMessages time").attr("datetime")).isEqualTo(own.getCreatedAt().toString());
            assertThat(html.selectFirst("main").text()).doesNotContain("??ui.inbox.");
            var list=mvc.perform(get("/inbox").param("lang",locale).with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn();
            var index=Jsoup.parse(list.getResponse().getContentAsString());
            assertThat(index.select("details.inbox-notifications:not([open])")).hasSize(1);
            assertThat(index.select("script[src^='/js/messaging/inbox.js']")).hasSize(1);
            var conversation=index.selectFirst("#inboxThreadList");var notifications=index.selectFirst(".inbox-notifications");
            assertThat(conversation.elementSiblingIndex()).isLessThan(notifications.elementSiblingIndex());
        }
        jdbc.update("update trainer_client_links set status='PAUSED' where id=?",link.getId());
        mvc.perform(post("/api/inbox/threads/{id}/send",thread.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .contentType("application/json").content("{\"bodyText\":\"Must not send from stale active link\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.reason").value("THREAD_LOCKED"));
        assertThat(messages.findByThread_IdOrderByCreatedAtAscIdAsc(thread.getId())).hasSize(1);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void fourActualOverlappingReadTransactionsCreateOneReceiptPerRecipientMessage() throws Exception {
        for(int i=0;i<8;i++) messaging.sendMessage(thread.getId(),coach.getId(),MessageType.TEXT,"Concurrent receipt "+i);
        var ids=inbox.getMessages(owner,thread.getId()).stream().map(Message::getId).toList();
        var executor=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);
        try {
            var tasks=new java.util.ArrayList<Future<?>>();
            for(int i=0;i<4;i++) tasks.add(executor.submit(() -> { start.await();inbox.markRead(owner,thread.getId(),ids.getLast());return null; }));
            start.countDown();for(var task:tasks) task.get(15,TimeUnit.SECONDS);
            assertThat(reads.findByUserIdAndMessageIdIn(owner.getId(),ids)).hasSize(8);
            assertThat(inbox.listConversations(owner).getFirst().getUnreadCount()).isZero();
        } finally { executor.shutdownNow(); }
    }

    private Message record(User sender,String body,Instant time) {
        var message=new Message(thread,sender.getId(),MessageType.TEXT,body);
        org.springframework.test.util.ReflectionTestUtils.setField(message,"createdAt",time);return messages.saveAndFlush(message);
    }
    private User account(Role role) { String name="inbox-"+UUID.randomUUID();var account=new User(name+"@example.invalid","Local",role.name(),name,"test-password");account.setRole(role);return users.saveAndFlush(account); }
}
