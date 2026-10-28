package com.example.media.ai;

import com.example.media.exception.InvalidMediaException;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
@Slf4j
public class ImagePreprocessor {

    private static final int MAX_EDGE_PX = 1024;
    private static final double JPEG_QUALITY = 0.85;

    public record ProcessedVisual(
            byte[] jpegBytes,
            String base64,
            String sha256Hex,
            int width,
            int height
    ) {}

    public ProcessedVisual process(byte[] rawBytes, String originalFilename, MediaVisualType visualType) {
        if (rawBytes == null || rawBytes.length == 0) {
            throw new InvalidMediaException("File is empty or missing");
        }

        BufferedImage sourceImage = null;

        // Check if rawBytes is a video file or visualType specifies VIDEO
        if (visualType == MediaVisualType.VIDEO || isMp4Video(rawBytes)) {
            sourceImage = extractKeyframeFromVideo(rawBytes);
        }

        // If not a video or video frame extraction didn't apply, validate as image
        if (sourceImage == null) {
            validateImageMagicBytes(rawBytes);
            try {
                sourceImage = ImageIO.read(new ByteArrayInputStream(rawBytes));
            } catch (Exception e) {
                log.warn("Failed to decode image bytes: {}", e.getMessage());
                throw new InvalidMediaException("Invalid or corrupted image file");
            }
        }

        if (sourceImage == null) {
            throw new InvalidMediaException("Could not read visual media content");
        }

        try {
            // Resize image keeping aspect ratio (max edge 1024px) & compress to JPEG quality 0.85
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(sourceImage)
                    .size(MAX_EDGE_PX, MAX_EDGE_PX)
                    .outputFormat("jpg")
                    .outputQuality(JPEG_QUALITY)
                    .toOutputStream(outputStream);

            byte[] jpegBytes = outputStream.toByteArray();
            String base64 = Base64.getEncoder().encodeToString(jpegBytes);
            String sha256Hex = computeSha256(jpegBytes);

            return new ProcessedVisual(
                    jpegBytes,
                    base64,
                    sha256Hex,
                    sourceImage.getWidth(),
                    sourceImage.getHeight()
            );
        } catch (Exception e) {
            log.error("Failed to preprocess visual media: {}", e.getMessage(), e);
            throw new InvalidMediaException("Failed to preprocess media for AI vision analysis");
        }
    }

    private BufferedImage extractKeyframeFromVideo(byte[] videoBytes) {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("ai-video-frame-", ".mp4");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(videoBytes);
            }
            Picture picture = FrameGrab.getFrameFromFile(tempFile, 0);
            if (picture != null) {
                return AWTUtil.toBufferedImage(picture);
            }
        } catch (Exception e) {
            log.warn("JCodec frame extraction failed: {}. Falling back to standard image decoding.", e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
        return null;
    }

    public void validateImageMagicBytes(byte[] bytes) {
        if (bytes.length < 12) {
            throw new InvalidMediaException("File is too small to be a valid image");
        }

        // JPEG: FF D8 FF
        boolean isJpeg = (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF);
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        boolean isPng = (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E && bytes[3] == (byte) 0x47);
        // WEBP: "RIFF" .... "WEBP"
        boolean isWebp = (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P');

        if (!isJpeg && !isPng && !isWebp) {
            throw new InvalidMediaException("Unsupported file type. Only JPEG, PNG, and WEBP image formats are supported");
        }
    }

    public boolean isMp4Video(byte[] bytes) {
        if (bytes.length < 12) return false;
        // ISO Base Media File Format signature: bytes 4..7 are "ftyp"
        return bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p';
    }

    public String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
