package com.example.recommender.batch;

import com.example.recommender.batch.job.userrecommendation.UserRecommendationProcessor;
import com.example.recommender.entity.RecommenderUserRecommendation;
import com.example.recommender.exception.MalformedPostException;
import com.example.recommender.repository.RecommenderBlockViewRepository;
import com.example.recommender.repository.RecommenderFollowViewRepository;
import com.example.recommender.repository.RecommenderMuteViewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRecommendationProcessorTest {

    @Mock
    private RecommenderFollowViewRepository followViewRepository;

    @Mock
    private RecommenderBlockViewRepository blockViewRepository;

    @Mock
    private RecommenderMuteViewRepository muteViewRepository;

    @InjectMocks
    private UserRecommendationProcessor processor;

    @Test
    @DisplayName("User hợp lệ có bạn chung: gợi ý danh sách bạn tiềm năng")
    void testProcessValidUser_ShouldReturnRecommendations() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID friend1 = UUID.randomUUID();
        UUID friend2 = UUID.randomUUID();

        when(followViewRepository.findFollowingIds(userId)).thenReturn(List.of(friend1));
        when(blockViewRepository.findAllBlockedAndBlockerUserIds(userId)).thenReturn(Collections.emptyList());
        when(muteViewRepository.findMutedUserIds(userId)).thenReturn(Collections.emptyList());

        // Friend of friend: friend2 có 3 người bạn chung
        List<Object[]> fofData = List.<Object[]>of(
                new Object[]{friend2, 3L}
        );
        when(followViewRepository.findFriendOfFriendsWithMutualCount(userId)).thenReturn(fofData);

        List<RecommenderUserRecommendation> recs = processor.process(userId);

        assertNotNull(recs);
        assertEquals(1, recs.size());
        assertEquals(userId, recs.get(0).getUserId());
        assertEquals(friend2, recs.get(0).getRecommendedUserId());
        assertEquals(3L, recs.get(0).getMutualFriendsCount());
        assertTrue(recs.get(0).getScore() > 0.5);
    }

    @Test
    @DisplayName("UserId bị null: ném MalformedPostException")
    void testProcessNullUserId_ShouldThrowMalformedPostException() {
        assertThrows(MalformedPostException.class, () -> processor.process(null));
    }
}
