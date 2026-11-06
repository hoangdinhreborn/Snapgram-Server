package com.example.recommender.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BatchPropertiesTest {

    @Test
    @DisplayName("BatchProperties nạp đúng giá trị mặc định")
    void testDefaultValues() {
        BatchProperties properties = new BatchProperties();

        assertEquals(20, properties.getChunkSize());
        assertEquals("0 0 */2 * * *", properties.getTrendingCron());
        assertEquals("0 0 3 * * *", properties.getUserRecCron());
        assertFalse(properties.isScheduledEnabled());
        assertEquals(100, properties.getSkipLimit());
    }

    @Test
    @DisplayName("BatchProperties cho phép cập nhật giá trị")
    void testSetters() {
        BatchProperties properties = new BatchProperties();
        properties.setChunkSize(50);
        properties.setSkipLimit(20);
        properties.setScheduledEnabled(true);
        properties.setTrendingCron("0 0 * * * *");
        properties.setUserRecCron("0 30 1 * * *");

        assertEquals(50, properties.getChunkSize());
        assertEquals(20, properties.getSkipLimit());
        assertTrue(properties.isScheduledEnabled());
        assertEquals("0 0 * * * *", properties.getTrendingCron());
        assertEquals("0 30 1 * * *", properties.getUserRecCron());
    }
}
