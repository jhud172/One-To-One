package uk.ac.cf._5.group14.One_To_One.Inbox;

import uk.ac.cf._5.group14.One_To_One.Inbox.dto.ConversationListItemDto;
import uk.ac.cf._5.group14.One_To_One.Messaging.Message;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThread;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;

public interface InboxService {
    List<ConversationListItemDto> listConversations(User user);
    MessageThread getConversationOrThrow(User user, Long threadId);
    List<Message> getMessages(User user, Long threadId);
    void markRead(User user, Long threadId);
    void markRead(User user, Long threadId, Long upToId);
    Message sendMessage(User user, Long threadId, String body, String attachmentName, String attachmentUrl, String attachmentType);
    Long startOrGetDirectConversation(User currentUser, Long otherUserId);
}
