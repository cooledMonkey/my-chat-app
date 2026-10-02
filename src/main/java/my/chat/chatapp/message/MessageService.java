package my.chat.chatapp.message;

import lombok.RequiredArgsConstructor;
import my.chat.chatapp.channel.ChannelService;
import my.chat.chatapp.common.BadRequestException;
import my.chat.chatapp.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final ChannelService channelService;
    private final UserRepository userRepository;

    public Mono<MessageView> send(Long channelId, Long authorId, String text) {
        if (text == null || text.isBlank()) {
            return Mono.error(new BadRequestException("message text is required"));
        }
        return channelService.findForUser(channelId, authorId)
                .then(userRepository.findById(authorId))
                .flatMap(author -> messageRepository.save(Message.builder()
                                .channelId(channelId)
                                .authorId(authorId)
                                .text(text)
                                .sentAt(Instant.now())
                                .build())
                        .map(message -> MessageView.from(message, author)));
    }

    public Flux<MessageView> history(Long channelId, Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            return Flux.error(new BadRequestException(
                    "page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE));
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "sentAt", "id"));
        return channelService.findForUser(channelId, userId)
                .thenMany(Flux.defer(() -> messageRepository.findAllByChannelId(channelId, pageable)))
                .flatMapSequential(message -> userRepository.findById(message.getAuthorId())
                        .map(author -> MessageView.from(message, author)));
    }
}
