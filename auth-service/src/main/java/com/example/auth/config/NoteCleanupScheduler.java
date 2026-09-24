package com.example.auth.config;

import com.example.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NoteCleanupScheduler {

    private final UserService userService;

    /**
     * Delete expired notes every hour.
     * fixedDelay = 3_600_000 ms = 1 hour, first run after 1 hour of startup.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void cleanupExpiredNotes() {
        log.debug("Running expired notes cleanup...");
        userService.deleteExpiredNotes();
    }
}
