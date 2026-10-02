package my.chat.chatapp.channel;

import lombok.RequiredArgsConstructor;
import my.chat.chatapp.common.AccessDeniedException;
import my.chat.chatapp.common.BadRequestException;
import my.chat.chatapp.common.NotFoundException;
import my.chat.chatapp.user.UserRepository;
import my.chat.chatapp.user.UserView;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final UserRepository userRepository;

    public Mono<Channel> createGroup(String name, Long creatorId) {
        if (name == null || name.isBlank()) {
            return Mono.error(new BadRequestException("channel name is required"));
        }
        return channelRepository.save(Channel.builder()
                        .name(name.trim())
                        .type(ChannelType.GROUP)
                        .createdBy(creatorId)
                        .createdAt(Instant.now())
                        .build())
                .flatMap(channel -> join(channel.getId(), creatorId).thenReturn(channel));
    }

    public Mono<Channel> createOrGetDirect(Long currentUserId, Long otherUserId) {
        if (otherUserId == null) {
            return Mono.error(new BadRequestException("userId is required"));
        }
        if (otherUserId.equals(currentUserId)) {
            return Mono.error(new BadRequestException("cannot create a direct channel with yourself"));
        }
        return userRepository.findById(otherUserId)
                .switchIfEmpty(Mono.error(new NotFoundException("user not found: " + otherUserId)))
                .flatMap(user -> channelRepository.findDirectChannel(currentUserId, otherUserId)
                        .switchIfEmpty(Mono.defer(() -> createDirect(currentUserId, otherUserId))));
    }

    private Mono<Channel> createDirect(Long currentUserId, Long otherUserId) {
        return channelRepository.save(Channel.builder()
                        .type(ChannelType.DIRECT)
                        .createdBy(currentUserId)
                        .createdAt(Instant.now())
                        .build())
                .flatMap(channel -> join(channel.getId(), currentUserId)
                        .then(join(channel.getId(), otherUserId))
                        .thenReturn(channel));
    }

    public Flux<Channel> listForUser(Long userId) {
        return channelMemberRepository.findAllByUserId(userId)
                .flatMapSequential(member -> channelRepository.findById(member.getChannelId()));
    }

    public Mono<ChannelMember> addMember(Long channelId, Long userId) {
        if (userId == null) {
            return Mono.error(new BadRequestException("userId is required"));
        }
        return channelRepository.findById(channelId)
                .switchIfEmpty(Mono.error(new NotFoundException("channel not found: " + channelId)))
                .flatMap(channel -> userRepository.findById(userId)
                        .switchIfEmpty(Mono.error(new NotFoundException("user not found: " + userId))))
                .flatMap(user -> join(channelId, userId));
    }

    public Mono<Boolean> isMember(Long channelId, Long userId) {
        return channelMemberRepository.existsByChannelIdAndUserId(channelId, userId);
    }

    public Mono<Channel> findForUser(Long channelId, Long userId) {
        return channelRepository.findById(channelId)
                .switchIfEmpty(Mono.error(new NotFoundException("channel not found: " + channelId)))
                .filterWhen(channel -> channelMemberRepository.existsByChannelIdAndUserId(channelId, userId))
                .switchIfEmpty(Mono.error(new AccessDeniedException("you are not a member of this channel")));
    }

    public Mono<ChannelView> getChannelView(Long channelId, Long userId) {
        return findForUser(channelId, userId)
                .flatMap(this::toView);
    }

    public Mono<ChannelView> toView(Channel channel) {
        return channelMemberRepository.findAllByChannelId(channel.getId())
                .flatMapSequential(member -> userRepository.findById(member.getUserId()))
                .map(UserView::from)
                .collectList()
                .map(members -> ChannelView.from(channel, members));
    }

    private Mono<ChannelMember> join(Long channelId, Long userId) {
        return channelMemberRepository.findByChannelIdAndUserId(channelId, userId)
                .switchIfEmpty(Mono.defer(() -> channelMemberRepository.save(
                        ChannelMember.builder().channelId(channelId).userId(userId).build())));
    }
}
