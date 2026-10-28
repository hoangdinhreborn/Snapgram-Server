package com.example.media.ai;

import com.example.media.exception.InvalidMediaException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImagePreprocessorTest {

    private final ImagePreprocessor preprocessor = new ImagePreprocessor();

    private byte[] createSampleJpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return baos.toByteArray();
    }

    private byte[] createSamplePng(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Process valid JPEG image: should resize, compress and compute hash")
    void process_ValidJpeg_ShouldSucceed() throws IOException {
        byte[] jpegBytes = createSampleJpeg(1200, 800);

        ImagePreprocessor.ProcessedVisual result = preprocessor.process(jpegBytes, "test.jpg", MediaVisualType.IMAGE);

        assertThat(result).isNotNull();
        assertThat(result.jpegBytes()).isNotEmpty();
        assertThat(result.base64()).isNotEmpty();
        assertThat(result.sha256Hex()).hasSize(64);
        assertThat(result.width()).isEqualTo(1200);
        assertThat(result.height()).isEqualTo(800);
    }

    @Test
    @DisplayName("Process valid PNG image: should convert to JPEG and compute sha256")
    void process_ValidPng_ShouldSucceed() throws IOException {
        byte[] pngBytes = createSamplePng(500, 500);

        ImagePreprocessor.ProcessedVisual result = preprocessor.process(pngBytes, "test.png", MediaVisualType.IMAGE);

        assertThat(result).isNotNull();
        assertThat(result.jpegBytes()).isNotEmpty();
        assertThat(result.sha256Hex()).hasSize(64);
    }

    @Test
    @DisplayName("Magic bytes validator rejects fake executable file disguised as jpg")
    void validateImageMagicBytes_FakeImage_ShouldThrowInvalidMedia() {
        byte[] fakeBytes = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        assertThatThrownBy(() -> preprocessor.validateImageMagicBytes(fakeBytes))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    @DisplayName("Empty or tiny byte array should throw InvalidMediaException")
    void process_EmptyBytes_ShouldThrowInvalidMedia() {
        assertThatThrownBy(() -> preprocessor.process(new byte[0], "empty.jpg", MediaVisualType.IMAGE))
                .isInstanceOf(InvalidMediaException.class)
                .hasMessageContaining("File is empty");
    }

    @Test
    @DisplayName("SHA-256 computation is deterministic for same input")
    void computeSha256_ShouldBeConsistent() {
        byte[] input = "Snapgram AI Caption Test Content".getBytes();
        String hash1 = preprocessor.computeSha256(input);
        String hash2 = preprocessor.computeSha256(input);

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }
}
