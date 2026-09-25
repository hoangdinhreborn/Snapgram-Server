package com.example.media.service;

import net.coobird.thumbnailator.Thumbnails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
@Slf4j
public class ImageProcessingService {

    private static final int THUMB_WIDTH = 400;
    private static final int THUMB_HEIGHT = 400;
    private static final double THUMB_QUALITY = 0.85;

    public record Dimensions(Integer width, Integer height) {}

    public Dimensions extractDimensions(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image != null) {
                return new Dimensions(image.getWidth(), image.getHeight());
            }
        } catch (Exception e) {
            log.warn("Failed to extract image dimensions: {}", e.getMessage());
        }
        return new Dimensions(null, null);
    }

    public byte[] generateThumbnail(byte[] imageBytes) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(new ByteArrayInputStream(imageBytes))
                    .size(THUMB_WIDTH, THUMB_HEIGHT)
                    .outputFormat("jpg")
                    .outputQuality(THUMB_QUALITY)
                    .toOutputStream(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.warn("Failed to generate thumbnail: {}", e.getMessage());
            return null;
        }
    }
}
