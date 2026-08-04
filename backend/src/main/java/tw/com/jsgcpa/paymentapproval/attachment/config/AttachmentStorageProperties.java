package tw.com.jsgcpa.paymentapproval.attachment.config;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "payment-approval.attachment")
public class AttachmentStorageProperties {

    @NotNull
    private Path storageRoot;

    @NotNull
    private DataSize maxFileSize;

    @NotEmpty
    private Set<String> allowedContentTypes = new TreeSet<>();

    @NotEmpty
    private Set<String> allowedExtensions = new TreeSet<>();

    public Path getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(Path storageRoot) {
        this.storageRoot = storageRoot;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public Set<String> getAllowedContentTypes() {
        return Set.copyOf(allowedContentTypes);
    }

    public void setAllowedContentTypes(Set<String> allowedContentTypes) {
        this.allowedContentTypes = normalize(allowedContentTypes);
    }

    public Set<String> getAllowedExtensions() {
        return Set.copyOf(allowedExtensions);
    }

    public void setAllowedExtensions(Set<String> allowedExtensions) {
        this.allowedExtensions = normalize(allowedExtensions);
    }

    @AssertTrue(message = "max-file-size must be greater than zero")
    public boolean isMaxFileSizePositive() {
        return maxFileSize != null && maxFileSize.toBytes() > 0;
    }

    private Set<String> normalize(Set<String> values) {
        if (values == null) {
            return new TreeSet<>();
        }
        TreeSet<String> normalized = new TreeSet<>();
        values.stream()
                .filter(value -> value != null)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(normalized::add);
        return normalized;
    }
}
