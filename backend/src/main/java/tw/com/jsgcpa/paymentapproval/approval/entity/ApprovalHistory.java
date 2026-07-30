package tw.com.jsgcpa.paymentapproval.approval.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.approval.enums.*;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;

@Entity
@Table(name = "approval_histories")
public class ApprovalHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_request_id", nullable = false) private PaymentRequest paymentRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id", nullable = false) private AppUser actor;
    @Enumerated(EnumType.STRING) @Column(name = "action", nullable = false, length = 30) private ApprovalAction action;
    @Enumerated(EnumType.STRING) @Column(name = "from_approval_status", length = 30) private ApprovalStatus fromApprovalStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_approval_status", length = 30) private ApprovalStatus toApprovalStatus;
    @Enumerated(EnumType.STRING) @Column(name = "from_payment_status", length = 20) private PaymentStatus fromPaymentStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_payment_status", length = 20) private PaymentStatus toPaymentStatus;
    @Column(name = "comment", columnDefinition = "TEXT") private String comment;
    @Column(name = "acted_at", nullable = false) private OffsetDateTime actedAt;
    @PrePersist protected void onCreate() { if (actedAt == null) actedAt = OffsetDateTime.now(); }
    public ApprovalHistory() {}
    public Long getId() { return id; }
    public PaymentRequest getPaymentRequest() { return paymentRequest; } public void setPaymentRequest(PaymentRequest v) { paymentRequest = v; }
    public AppUser getActor() { return actor; } public void setActor(AppUser v) { actor = v; }
    public ApprovalAction getAction() { return action; } public void setAction(ApprovalAction v) { action = v; }
    public ApprovalStatus getFromApprovalStatus() { return fromApprovalStatus; } public void setFromApprovalStatus(ApprovalStatus v) { fromApprovalStatus = v; }
    public ApprovalStatus getToApprovalStatus() { return toApprovalStatus; } public void setToApprovalStatus(ApprovalStatus v) { toApprovalStatus = v; }
    public PaymentStatus getFromPaymentStatus() { return fromPaymentStatus; } public void setFromPaymentStatus(PaymentStatus v) { fromPaymentStatus = v; }
    public PaymentStatus getToPaymentStatus() { return toPaymentStatus; } public void setToPaymentStatus(PaymentStatus v) { toPaymentStatus = v; }
    public String getComment() { return comment; } public void setComment(String v) { comment = v; }
    public OffsetDateTime getActedAt() { return actedAt; } public void setActedAt(OffsetDateTime v) { actedAt = v; }
}
