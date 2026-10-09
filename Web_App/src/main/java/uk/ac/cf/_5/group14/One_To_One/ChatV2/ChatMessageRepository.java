package uk.ac.cf._5.group14.One_To_One.ChatV2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Pageable;

@Repository("chatV2MessageRepository")
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByThreadOrderByCreatedAtAsc(ChatThread thread);
    List<ChatMessage> findByThreadOrderByCreatedAtAscIdAsc(ChatThread thread, Pageable page);
    long countByThread(ChatThread thread);
}
