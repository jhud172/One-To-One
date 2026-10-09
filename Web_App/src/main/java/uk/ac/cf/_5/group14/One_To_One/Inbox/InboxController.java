package uk.ac.cf._5.group14.One_To_One.Inbox;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThread;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessagingException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/inbox")
public class InboxController {

    private final InboxService inboxService;
    private final AuthHelper authHelper;
    private final UserRepository userRepository;
    private final uk.ac.cf._5.group14.One_To_One.Messaging.MessageReadStateRepository readStates;

    public InboxController(InboxService inboxService, AuthHelper authHelper, UserRepository userRepository,
                           uk.ac.cf._5.group14.One_To_One.Messaging.MessageReadStateRepository readStates) {
        this.inboxService = inboxService;
        this.authHelper = authHelper;
        this.userRepository = userRepository;
        this.readStates = readStates;
    }

    @GetMapping
    public ModelAndView inbox(java.util.Locale locale) {
        User user = authHelper.getAuthenticatedUser();
        ModelAndView mav = new ModelAndView("shared-views/inbox/index");
        addConversations(mav, user, locale);
        return mav;
    }

    @GetMapping("/{conversationId}")
    public ModelAndView thread(@PathVariable Long conversationId, Model model, java.util.Locale locale) {
        User user = authHelper.getAuthenticatedUser();
        MessageThread thread;
        try {
            thread = inboxService.getConversationOrThrow(user, conversationId);
        } catch (IllegalArgumentException missingConversation) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var messages = inboxService.getMessages(user, conversationId);
        inboxService.markRead(user, conversationId, messages.stream().map(uk.ac.cf._5.group14.One_To_One.Messaging.Message::getId).max(Long::compareTo).orElse(null));

        Long otherUserId = user != null && user.getId() != null && user.getId().equals(thread.getClientId())
                ? thread.getTrainerId()
                : thread.getClientId();
        User otherUser = userRepository.findById(otherUserId).orElse(null);

        ModelAndView mav = new ModelAndView("shared-views/inbox/thread");
        for (String key : new String[]{"inboxSent", "inboxSendError", "inboxDraftBody", "inboxDraftAttachment", "inboxCheckinDraft", "inboxCheckinMood", "inboxCheckinEnergy", "inboxCheckinNotes"}) {
            if (model.containsAttribute(key)) mav.addObject(key, model.getAttribute(key));
        }
        addConversations(mav, user, locale);
        mav.addObject("thread", thread);
        mav.addObject("messages", messages);
        var dates = new java.util.HashMap<Long, String>();
        var attachments = new java.util.HashMap<Long, String>();
        for (var message : messages) {
            dates.put(message.getId(), displayDate(message.getCreatedAt(), locale));
            String attachment = message.getAttachmentUrl();
            if (attachment != null && !attachment.isBlank() && uk.ac.cf._5.group14.One_To_One.Security.SafeHttpUrl.isSafe(attachment))
                attachments.put(message.getId(), attachment.trim());
        }
        mav.addObject("inboxMessageDates", dates);
        mav.addObject("inboxAttachmentLinks", attachments);
        mav.addObject("inboxReadMessages", messages.isEmpty() ? java.util.Set.of() : readStates
                .findByUserIdAndMessageIdIn(otherUserId, messages.stream().map(uk.ac.cf._5.group14.One_To_One.Messaging.Message::getId).toList())
                .stream().map(uk.ac.cf._5.group14.One_To_One.Messaging.MessageReadState::getMessageId).collect(java.util.stream.Collectors.toSet()));
        mav.addObject("conversationId", conversationId);
        mav.addObject("currentUserId", user != null ? user.getId() : null);
        mav.addObject("otherUser", otherUser);
        return mav;
    }

    private void addConversations(ModelAndView mav, User user, java.util.Locale locale) {
        var conversations = inboxService.listConversations(user);
        var dates = new java.util.HashMap<Long, String>();
        for (var conversation : conversations) dates.put(conversation.getConversationId(), displayDate(conversation.getLastMessageAt(), locale));
        mav.addObject("conversations", conversations);
        mav.addObject("inboxConversationDates", dates);
    }

    private String displayDate(java.time.Instant date, java.util.Locale locale) {
        return date == null ? "" : java.time.format.DateTimeFormatter
                .ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM, java.time.format.FormatStyle.SHORT)
                .withLocale(locale).withZone(java.time.ZoneId.systemDefault()).format(date);
    }

    @PostMapping("/{conversationId}/send")
    public ModelAndView send(@PathVariable Long conversationId, @RequestParam(value = "body", required = false) String body,
                            @RequestParam(value = "attachmentUrl", required = false) String attachmentUrl,
                            RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        try {
            inboxService.sendMessage(user, conversationId, body, null, attachmentUrl, "link");
            redirectAttributes.addFlashAttribute("inboxSent", true);
        } catch (MessagingException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("inboxSendError", exception instanceof MessagingException messagingException
                    ? messagingException.getReason().name() : "INVALID_MESSAGE");
            redirectAttributes.addFlashAttribute("inboxDraftBody", body);
            redirectAttributes.addFlashAttribute("inboxDraftAttachment", attachmentUrl);
        }
        return new ModelAndView("redirect:/inbox/" + conversationId);
    }

    @PostMapping("/start/{otherUserId}")
    public ModelAndView start(@PathVariable Long otherUserId) {
        User user = authHelper.getAuthenticatedUser();
        Long conversationId = inboxService.startOrGetDirectConversation(user, otherUserId);
        return new ModelAndView("redirect:/inbox/" + conversationId);
    }
}
