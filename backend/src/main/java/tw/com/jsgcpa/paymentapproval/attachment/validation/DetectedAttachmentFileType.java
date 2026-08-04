package tw.com.jsgcpa.paymentapproval.attachment.validation;

public enum DetectedAttachmentFileType {
    PDF("application/pdf", "pdf", new byte[]{
            0x25, 0x50, 0x44, 0x46, 0x2D
    }),
    JPEG("image/jpeg", "jpg", new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    }),
    PNG("image/png", "png", new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    });

    private final String contentType;
    private final String canonicalExtension;
    private final byte[] signature;

    DetectedAttachmentFileType(
            String contentType,
            String canonicalExtension,
            byte[] signature
    ) {
        this.contentType = contentType;
        this.canonicalExtension = canonicalExtension;
        this.signature = signature;
    }

    public String getContentType() {
        return contentType;
    }

    public String getCanonicalExtension() {
        return canonicalExtension;
    }

    public boolean matches(byte[] content) {
        if (content == null || content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }
}
