package com.example.realtimechatonline.service;

import com.example.realtimechatonline.domain.dto.response.UserPresenceDto;
import com.example.realtimechatonline.domain.dto.websocket.PresenceEventDto;
import com.example.realtimechatonline.domain.entity.User;
import com.example.realtimechatonline.repository.UserRepository;
import com.example.realtimechatonline.service.impl.PresenceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PresenceServiceImpl Unit & Concurrency Tests")
class PresenceServiceTest {

    @Mock
    private ObjectProvider<SimpMessagingTemplate> messagingTemplateProvider;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PresenceServiceImpl presenceService;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        lenient().when(messagingTemplateProvider.getIfAvailable()).thenReturn(messagingTemplate);
        alice = User.builder().id(1L).username("alice").fullname("Alice Carroll").build();
        bob = User.builder().id(2L).username("bob").fullname("Bob Builder").build();
    }

    @Test
    @DisplayName("Scenario 1: User first session connects -> marks ONLINE and broadcasts event")
    void firstSession_connects_userBecomesOnline() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        presenceService.addSession("alice", "sess-1");

        assertThat(presenceService.isUserOnline("alice")).isTrue();
        assertThat(presenceService.getOnlineUsernames()).contains("alice");

        ArgumentCaptor<PresenceEventDto> captor = ArgumentCaptor.forClass(PresenceEventDto.class);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/presence"), captor.capture());

        PresenceEventDto event = captor.getValue();
        assertThat(event.getUsername()).isEqualTo("alice");
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.isOnline()).isTrue();
    }

    @Test
    @DisplayName("Scenario 2 & 3: Multi-session — 2 tabs open, 1 tab closes -> still ONLINE; last tab closes -> OFFLINE")
    void multiSession_oneCloses_stillOnline_lastCloses_becomesOffline() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        // Tab 1 connects
        presenceService.addSession("alice", "sess-1");
        assertThat(presenceService.isUserOnline("alice")).isTrue();

        // Tab 2 connects
        presenceService.addSession("alice", "sess-2");
        assertThat(presenceService.isUserOnline("alice")).isTrue();

        // Verify only 1 broadcast was made for first connect
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/presence"), any(PresenceEventDto.class));

        // Tab 1 closes -> still online because Tab 2 is active
        presenceService.removeSession("sess-1");
        assertThat(presenceService.isUserOnline("alice")).isTrue();
        // Still only 1 broadcast (no offline broadcast yet)
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/presence"), any(PresenceEventDto.class));

        // Tab 2 closes -> last session closes -> transitions to OFFLINE
        presenceService.removeSession("sess-2");
        assertThat(presenceService.isUserOnline("alice")).isFalse();
        assertThat(presenceService.getOnlineUsernames()).doesNotContain("alice");

        // Now offline broadcast must happen
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/presence"), any(PresenceEventDto.class));
        verify(userRepository).updateLastSeenAt(eq("alice"), any(Instant.class));
    }

    @Test
    @DisplayName("Scenario 4: Two sessions disconnect in any order -> handles cleanly")
    void twoSessions_disconnectAnyOrder_safe() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        presenceService.addSession("alice", "sess-1");
        presenceService.addSession("alice", "sess-2");

        // Disconnect sess-2 first, then sess-1
        presenceService.removeSession("sess-2");
        assertThat(presenceService.isUserOnline("alice")).isTrue();

        presenceService.removeSession("sess-1");
        assertThat(presenceService.isUserOnline("alice")).isFalse();
    }

    @Test
    @DisplayName("Scenario 5: Disconnect for non-existent session is safely ignored")
    void removeSession_nonExistent_ignoredSafely() {
        presenceService.removeSession("non-existent-session");

        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Scenario 6: Reconnect — reconnecting restores ONLINE status")
    void reconnect_restoresOnline() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        presenceService.addSession("alice", "sess-1");
        presenceService.removeSession("sess-1");
        assertThat(presenceService.isUserOnline("alice")).isFalse();

        // Reconnect with new session id
        presenceService.addSession("alice", "sess-new");
        assertThat(presenceService.isUserOnline("alice")).isTrue();
    }

    @Test
    @DisplayName("Scenario 7: getPresenceList returns accurate online status and lastSeenAt")
    void getPresenceList_returnsCorrectData() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));

        presenceService.addSession("alice", "sess-alice");
        // Bob is offline
        bob.setLastSeenAt(Instant.parse("2026-10-12T06:00:00Z"));

        List<UserPresenceDto> list = presenceService.getPresenceList(List.of("alice", "bob"));
        assertThat(list).hasSize(2);

        UserPresenceDto alicePresence = list.stream().filter(p -> p.getUsername().equals("alice")).findFirst().orElseThrow();
        assertThat(alicePresence.isOnline()).isTrue();
        assertThat(alicePresence.getLastSeenAt()).isNull();

        UserPresenceDto bobPresence = list.stream().filter(p -> p.getUsername().equals("bob")).findFirst().orElseThrow();
        assertThat(bobPresence.isOnline()).isFalse();
        assertThat(bobPresence.getLastSeenAt()).isEqualTo(Instant.parse("2026-10-12T06:00:00Z"));
    }

    @Test
    @DisplayName("Scenario 8: Concurrent session additions and removals maintain consistent state")
    void concurrentSessions_threadSafety() throws InterruptedException {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(alice));

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String sessId = "sess-" + i;
            executor.submit(() -> {
                try {
                    presenceService.addSession("alice", sessId);
                    presenceService.touchSession(sessId);
                    presenceService.removeSession(sessId);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Since all sessions were removed, user must be offline
        assertThat(presenceService.isUserOnline("alice")).isFalse();
    }

    @Test
    @DisplayName("Scenario 9: Stale session sweeper only sweeps sessions older than 90s, active sessions remain")
    @SuppressWarnings("unchecked")
    void sweepStaleSessions_onlySweepsOldSessions() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        presenceService.addSession("alice", "sess-active");
        presenceService.addSession("alice", "sess-stale");

        // Set sess-stale activity to 120 seconds ago
        ConcurrentHashMap<String, Instant> sessionLastActive =
                (ConcurrentHashMap<String, Instant>) org.springframework.test.util.ReflectionTestUtils
                        .getField(presenceService, "sessionLastActive");
        assertThat(sessionLastActive).isNotNull();
        sessionLastActive.put("sess-stale", Instant.now().minus(java.time.Duration.ofSeconds(120)));

        // Run sweep
        presenceService.sweepStaleSessions();

        // sess-stale should be removed, but sess-active still keeps alice online
        assertThat(presenceService.isUserOnline("alice")).isTrue();
        assertThat(sessionLastActive).containsKey("sess-active");
        assertThat(sessionLastActive).doesNotContainKey("sess-stale");
    }

    @Test
    @DisplayName("Scenario 10: Idempotent session removal — sweeping followed by disconnect event causes no duplicate offline events")
    void idempotentSessionRemoval_noDuplicateBroadcast() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        presenceService.addSession("alice", "sess-1");
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/presence"), any(PresenceEventDto.class));

        // First remove (e.g. by sweeper)
        presenceService.removeSession("sess-1");
        assertThat(presenceService.isUserOnline("alice")).isFalse();
        // 1 online + 1 offline = 2 total
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/presence"), any(PresenceEventDto.class));

        // Second remove (e.g. by subsequent SessionDisconnectEvent)
        presenceService.removeSession("sess-1");
        // Must still be 2 broadcasts total (no duplicate offline broadcast)
        verify(messagingTemplate, times(2)).convertAndSend(eq("/topic/presence"), any(PresenceEventDto.class));
    }

    @Test
    @DisplayName("Scenario 11: Concurrent disconnect of old session and connect of new session keeps user online")
    void concurrentDisconnectOld_connectNew_keepsOnline() throws InterruptedException {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        presenceService.addSession("alice", "sess-old");
        assertThat(presenceService.isUserOnline("alice")).isTrue();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                presenceService.removeSession("sess-old");
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                presenceService.addSession("alice", "sess-new");
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Alice connected sess-new, so alice must remain online
        assertThat(presenceService.isUserOnline("alice")).isTrue();
    }
}
