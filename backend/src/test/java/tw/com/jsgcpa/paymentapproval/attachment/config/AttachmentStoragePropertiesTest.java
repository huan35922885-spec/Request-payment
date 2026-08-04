package tw.com.jsgcpa.paymentapproval.attachment.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class AttachmentStoragePropertiesTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void normalizesAllowedValuesToLowerCase() {
        AttachmentStorageProperties properties = validProperties();

        properties.setAllowedContentTypes(Set.of(
                " application/pdf ",
                "IMAGE/PNG"
        ));
        properties.setAllowedExtensions(Set.of("PDF", "JpG"));

        assertEquals(
                Set.of("application/pdf", "image/png"),
                properties.getAllowedContentTypes()
        );
        assertEquals(
                Set.of("jpg", "pdf"),
                properties.getAllowedExtensions()
        );
    }

    @Test
    void acceptsPositiveFileSizeAndRequiredCollections() {
        AttachmentStorageProperties properties = validProperties();

        assertTrue(properties.isMaxFileSizePositive());
        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void rejectsZeroFileSize() {
        AttachmentStorageProperties properties = validProperties();
        properties.setMaxFileSize(DataSize.ofBytes(0));

        assertTrue(validator.validate(properties).stream()
                .anyMatch(error -> error.getMessage()
                        .contains("greater than zero")));
    }

    private AttachmentStorageProperties validProperties() {
        AttachmentStorageProperties properties = new AttachmentStorageProperties();
        properties.setStorageRoot(Path.of("data/attachments"));
        properties.setMaxFileSize(DataSize.ofMegabytes(10));
        properties.setAllowedContentTypes(Set.of("application/pdf"));
        properties.setAllowedExtensions(Set.of("pdf"));
        return properties;
    }
}
