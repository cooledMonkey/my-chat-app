package my.chat.chatapp.channel;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChannelMemberRepository extends ReactiveCrudRepository<ChannelMember, Long> {

    Flux<ChannelMember> findAllByUserId(Long userId);

    Flux<ChannelMember> findAllByChannelId(Long channelId);

    Mono<ChannelMember> findByChannelIdAndUserId(Long channelId, Long userId);

    Mono<Boolean> existsByChannelIdAndUserId(Long channelId, Long userId);
}
