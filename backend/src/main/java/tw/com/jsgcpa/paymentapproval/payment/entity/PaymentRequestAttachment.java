package tw.com.jsgcpa.paymentapproval.payment.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.enums.AttachmentType;

@Entity
@Table(name = "payment_request_attachments")
public class PaymentRequestAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_request_id", nullable = false) private PaymentRequest paymentRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by_id", nullable = false) private AppUser uploadedBy;
    @Enumerated(EnumType.STRING) @Column(name = "attachment_type", nullable = false, length = 30) private AttachmentType attachmentType;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Column(name = "stored_filename", nullable = false, length = 255) private String storedFilename;
    @Column(name = "storage_path", nullable = false, length = 500) private String storagePath;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "file_size", nullable = false) private Long fileSize;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @PrePersist protected void onCreate() { if (createdAt == null) createdAt = OffsetDateTime.now(); }
    public PaymentRequestAttachment() {}
    public Long getId() { return id; }
    public PaymentRequest getPaymentRequest() { return paymentRequest; } public void setPaymentRequest(PaymentRequest v) { paymentRequest = v; }
    public AppUser getUploadedBy() { return uploadedBy; } public void setUploadedBy(AppUser v) { uploadedBy = v; }
    public AttachmentType getAttachmentType() { return attachmentType; } public void setAttachmentType(AttachmentType v) { attachmentType = v; }
    public String getOriginalFilename() { return originalFilename; } public void setOriginalFilename(String v) { originalFilename = v; }
    public String getStoredFilename() { return storedFilename; } public void setStoredFilename(String v) { storedFilename = v; }
    public String getStoragePath() { return storagePath; } public void setStoragePath(String v) { storagePath = v; }
    public String getContentType() { return contentType; } public void setContentType(String v) { contentType = v; }
    public Long getFileSize() { return fileSize; } public void setFileSize(Long v) { fileSize = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
