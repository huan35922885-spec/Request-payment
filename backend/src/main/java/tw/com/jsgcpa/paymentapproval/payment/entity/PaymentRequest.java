package tw.com.jsgcpa.paymentapproval.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;
import tw.com.jsgcpa.paymentapproval.master.entity.Company;
import tw.com.jsgcpa.paymentapproval.master.entity.Customer;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.organization.entity.Department;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentMethod;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

@Entity
@Table(name = "payment_requests")
public class PaymentRequest extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "request_no", nullable = false, unique = true, length = 50) private String requestNo;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "applicant_id", nullable = false) private AppUser applicant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id", nullable = false) private Department department;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "supervisor_snapshot_id") private AppUser supervisorSnapshot;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id", nullable = false) private Customer customer;
    @Enumerated(EnumType.STRING) @Column(name = "request_category", nullable = false, length = 20) private RequestCategory requestCategory;
    @Column(name = "reason", nullable = false, columnDefinition = "TEXT") private String reason;
    @Enumerated(EnumType.STRING) @Column(name = "approval_status", nullable = false, length = 30) private ApprovalStatus approvalStatus = ApprovalStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 20) private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount = BigDecimal.ZERO;
    @Column(name = "submitted_at") private OffsetDateTime submittedAt;
    @Column(name = "approved_at") private OffsetDateTime approvedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "approved_by_id") private AppUser approvedBy;
    @Column(name = "rejected_at") private OffsetDateTime rejectedAt;
    @Column(name = "closed_at") private OffsetDateTime closedAt;
    @Column(name = "paid_at") private OffsetDateTime paidAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "paid_by_id") private AppUser paidBy;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", length = 30) private PaymentMethod paymentMethod;
    @Column(name = "payment_reference", length = 100) private String paymentReference;
    @Column(name = "payment_note", columnDefinition = "TEXT") private String paymentNote;
    @Version @Column(name = "version", nullable = false) private Long version;
    public PaymentRequest() {}
    public Long getId() { return id; }
    public String getRequestNo() { return requestNo; } public void setRequestNo(String v) { requestNo = v; }
    public AppUser getApplicant() { return applicant; } public void setApplicant(AppUser v) { applicant = v; }
    public Department getDepartment() { return department; } public void setDepartment(Department v) { department = v; }
    public AppUser getSupervisorSnapshot() { return supervisorSnapshot; } public void setSupervisorSnapshot(AppUser v) { supervisorSnapshot = v; }
    public Company getCompany() { return company; } public void setCompany(Company v) { company = v; }
    public Customer getCustomer() { return customer; } public void setCustomer(Customer v) { customer = v; }
    public RequestCategory getRequestCategory() { return requestCategory; } public void setRequestCategory(RequestCategory v) { requestCategory = v; }
    public String getReason() { return reason; } public void setReason(String v) { reason = v; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; } public void setApprovalStatus(ApprovalStatus v) { approvalStatus = v; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; } public void setPaymentStatus(PaymentStatus v) { paymentStatus = v; }
    public BigDecimal getTotalAmount() { return totalAmount; } public void setTotalAmount(BigDecimal v) { totalAmount = v; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; } public void setSubmittedAt(OffsetDateTime v) { submittedAt = v; }
    public OffsetDateTime getApprovedAt() { return approvedAt; } public void setApprovedAt(OffsetDateTime v) { approvedAt = v; }
    public AppUser getApprovedBy() { return approvedBy; } public void setApprovedBy(AppUser v) { approvedBy = v; }
    public OffsetDateTime getRejectedAt() { return rejectedAt; } public void setRejectedAt(OffsetDateTime v) { rejectedAt = v; }
    public OffsetDateTime getClosedAt() { return closedAt; } public void setClosedAt(OffsetDateTime v) { closedAt = v; }
    public OffsetDateTime getPaidAt() { return paidAt; } public void setPaidAt(OffsetDateTime v) { paidAt = v; }
    public AppUser getPaidBy() { return paidBy; } public void setPaidBy(AppUser v) { paidBy = v; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; } public void setPaymentMethod(PaymentMethod v) { paymentMethod = v; }
    public String getPaymentReference() { return paymentReference; } public void setPaymentReference(String v) { paymentReference = v; }
    public String getPaymentNote() { return paymentNote; } public void setPaymentNote(String v) { paymentNote = v; }
    public Long getVersion() { return version; }
}
