package com.example.content.service;

import com.example.content.dto.CreateStoryRequest;
import com.example.content.entity.Story;
import com.example.content.entity.Visibility;
import com.example.content.event.StoryCreatedEvent;
import com.example.content.repository.CloseFriendRepository;
import com.example.content.repository.FollowRepository;
import com.example.content.repository.StoryRepository;
import com.example.content.repository.StoryViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryServiceTest {

    @Mock
    private StoryRepository storyRepository;
    @Mock
    private StoryViewRepository storyViewRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private CloseFriendRepository closeFriendRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publicStoryEventContainsAcceptedFollowerIdsAndStableStoryId() {
        StoryService storyService = new StoryService(storyRepository, storyViewRepository, followRepository,
                closeFriendRepository, kafkaTemplate);
        ReflectionTestUtils.setField(storyService, "storyTtlHours", 24);
        UUID authorId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID followerId = UUID.randomUUID();
        when(followRepository.findAcceptedFollowerIds(authorId)).thenReturn(List.of(followerId));
        when(storyRepository.save(org.mockito.ArgumentMatchers.any(Story.class))).thenAnswer(invocation -> {
            Story saved = invocation.getArgument(0);
            saved.setId(storyId);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        CreateStoryRequest request = new CreateStoryRequest();
        request.setMediaUrl("https://example.test/story.jpg");
        request.setMediaType("IMAGE");
        request.setVisibility("PUBLIC");
        storyService.createStory(authorId, request);

        ArgumentCaptor<StoryCreatedEvent> eventCaptor = ArgumentCaptor.forClass(StoryCreatedEvent.class);
        verify(kafkaTemplate).send(eq("content.story-created"), eq(storyId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo(storyId.toString());
        assertThat(eventCaptor.getValue().getFollowerIds()).containsExactly(followerId.toString());
    }

    @Test
    void privateStoryDoesNotFanOutToFollowers() {
        StoryService storyService = new StoryService(storyRepository, storyViewRepository, followRepository,
                closeFriendRepository, kafkaTemplate);
        ReflectionTestUtils.setField(storyService, "storyTtlHours", 24);
        UUID authorId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        when(storyRepository.save(org.mockito.ArgumentMatchers.any(Story.class))).thenAnswer(invocation -> {
            Story saved = invocation.getArgument(0);
            saved.setId(storyId);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        CreateStoryRequest request = new CreateStoryRequest();
        request.setMediaUrl("https://example.test/story.jpg");
        request.setMediaType("IMAGE");
        request.setVisibility(Visibility.PRIVATE.name());
        storyService.createStory(authorId, request);

        ArgumentCaptor<StoryCreatedEvent> eventCaptor = ArgumentCaptor.forClass(StoryCreatedEvent.class);
        verify(kafkaTemplate).send(eq("content.story-created"), eq(storyId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getFollowerIds()).isEmpty();
        verify(followRepository, org.mockito.Mockito.never()).findAcceptedFollowerIds(authorId);
    }
}
