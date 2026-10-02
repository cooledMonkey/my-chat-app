package my.chat.chatapp.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface MessageRepository extends ReactiveCrudRepository<Message, Long> {

    Flux<Message> findAllByChannelId(Long channelId, Pageable pageable);
}
