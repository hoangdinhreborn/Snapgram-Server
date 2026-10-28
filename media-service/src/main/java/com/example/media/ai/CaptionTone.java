package com.example.media.ai;

public enum CaptionTone {
    FRIENDLY("thân thiện, gần gũi, ấm áp, tự nhiên"),
    FORMAL("lịch sự, trang trọng, chỉn chu, chuyên nghiệp"),
    FUNNY("hài hước, hóm hỉnh, dí dỏm, bắt trend giới trẻ"),
    SHORT("ngắn gọn, súc tích, tối giản, cô đọng");

    private final String description;

    CaptionTone(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
