package com.example.content.service;

import com.example.content.dto.CreatePostRequest;
import com.example.content.entity.Post;
import com.example.content.entity.PostStatus;
import com.example.content.event.EventIds;
import com.example.content.event.MentionEvent;
import com.example.content.event.PostCreatedEvent;
import com.example.content.repository.CloseFriendRepository;
import com.example.content.repository.FollowRepository;
import com.example.content.repository.HashtagRepository;
import com.example.content.repository.MuteRepository;
import com.example.content.repository.PostHashtagRepository;
import com.example.content.repository.PostMediaRepository;
import com.example.content.repository.PostMentionRepository;
import com.example.content.repository.PostRepository;
import com.example.content.repository.UserPrivacyViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceEventTest {

    @Mock private PostRepository postRepository;
    @Mock private PostMediaRepository postMediaRepository;
    @Mock private FollowRepository followRepository;
    @Mock private MuteRepository muteRepository;
    @Mock private CloseFriendRepository closeFriendRepository;
    @Mock private UserPrivacyViewRepository userPrivacyViewRepository;
    @Mock private HashtagRepository hashtagRepository;
    @Mock private PostHashtagRepository postHashtagRepository;
    @Mock private PostMentionRepository postMentionRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void postCreatedEventUsesPostIdAndAcceptedFollowers() {
        PostService postService = new PostService(postRepository, postMediaRepository, followRepository,
                muteRepository, closeFriendRepository, userPrivacyViewRepository, hashtagRepository,
                postHashtagRepository, postMentionRepository, kafkaTemplate);
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        when(followRepository.findAcceptedFollowerIds(authorId)).thenReturn(List.of(followerId));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setId(postId);
            saved.setStatus(PostStatus.PUBLISHED);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(saved.getCreatedAt());
            return saved;
        });

        postService.createPost(authorId, new CreatePostRequest());

        ArgumentCaptor<PostCreatedEvent> eventCaptor = ArgumentCaptor.forClass(PostCreatedEvent.class);
        verify(kafkaTemplate).send(eq("content.post-created"), eq(postId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo(postId.toString());
        assertThat(eventCaptor.getValue().getFollowerIds()).containsExactly(followerId.toString());
    }

    @Test
    void mentionEventIdIsStableForPostAndMentionedUser() {
        PostService postService = new PostService(postRepository, postMediaRepository, followRepository,
                muteRepository, closeFriendRepository, userPrivacyViewRepository, hashtagRepository,
                postHashtagRepository, postMentionRepository, kafkaTemplate);
        UUID authorId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID mentionedUserId = UUID.randomUUID();
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setId(postId);
            saved.setStatus(PostStatus.PUBLISHED);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(saved.getCreatedAt());
            return saved;
        });
        when(followRepository.findAcceptedFollowerIds(authorId)).thenReturn(List.of());

        CreatePostRequest request = new CreatePostRequest();
        request.setMentionedUserIds(List.of(mentionedUserId));
        postService.createPost(authorId, request);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2))
                .send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), eventCaptor.capture());
        MentionEvent mentionEvent = eventCaptor.getAllValues().stream()
                .filter(MentionEvent.class::isInstance)
                .map(MentionEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(mentionEvent.getEventId()).isEqualTo(
                EventIds.stableFor("mention.events", postId.toString(), mentionedUserId.toString()));
    }
}
