package tw.com.jsgcpa.paymentapproval.payment.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import tw.com.jsgcpa.paymentapproval.approval.enums.ApprovalStatus;
import tw.com.jsgcpa.paymentapproval.payment.entity.PaymentRequest;
import tw.com.jsgcpa.paymentapproval.payment.enums.PaymentStatus;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    @Override
    @EntityGraph(attributePaths = {
            "applicant",
            "department",
            "supervisorSnapshot",
            "company",
            "customer",
            "approvedBy",
            "paidBy"
    })
    Optional<PaymentRequest> findById(Long id);

    @Query(
            value = """
                    select distinct paymentRequest
                    from PaymentRequest paymentRequest
                    join fetch paymentRequest.applicant
                    join fetch paymentRequest.department
                    left join fetch paymentRequest.supervisorSnapshot
                    join fetch paymentRequest.company
                    join fetch paymentRequest.customer
                    where (cast(:requestNo as String) is null
                        or lower(paymentRequest.requestNo)
                            like lower(concat('%', cast(:requestNo as String), '%')))
                      and (cast(:approvalStatus as String) is null
                        or paymentRequest.approvalStatus = :approvalStatus)
                      and (cast(:paymentStatus as String) is null
                        or paymentRequest.paymentStatus = :paymentStatus)
                      and (cast(:requestCategory as String) is null
                        or paymentRequest.requestCategory = :requestCategory)
                      and (cast(:applicantId as Long) is null
                        or paymentRequest.applicant.id = :applicantId)
                      and (cast(:departmentId as Long) is null
                        or paymentRequest.department.id = :departmentId)
                      and (cast(:companyId as Long) is null
                        or paymentRequest.company.id = :companyId)
                      and (cast(:customerId as Long) is null
                        or paymentRequest.customer.id = :customerId)
                      and (cast(:createdFrom as OffsetDateTime) is null
                        or paymentRequest.createdAt >= :createdFrom)
                      and (cast(:createdToExclusive as OffsetDateTime) is null
                        or paymentRequest.createdAt < :createdToExclusive)
                    """,
            countQuery = """
                    select count(paymentRequest)
                    from PaymentRequest paymentRequest
                    where (cast(:requestNo as String) is null
                        or lower(paymentRequest.requestNo)
                            like lower(concat('%', cast(:requestNo as String), '%')))
                      and (cast(:approvalStatus as String) is null
                        or paymentRequest.approvalStatus = :approvalStatus)
                      and (cast(:paymentStatus as String) is null
                        or paymentRequest.paymentStatus = :paymentStatus)
                      and (cast(:requestCategory as String) is null
                        or paymentRequest.requestCategory = :requestCategory)
                      and (cast(:applicantId as Long) is null
                        or paymentRequest.applicant.id = :applicantId)
                      and (cast(:departmentId as Long) is null
                        or paymentRequest.department.id = :departmentId)
                      and (cast(:companyId as Long) is null
                        or paymentRequest.company.id = :companyId)
                      and (cast(:customerId as Long) is null
                        or paymentRequest.customer.id = :customerId)
                      and (cast(:createdFrom as OffsetDateTime) is null
                        or paymentRequest.createdAt >= :createdFrom)
                      and (cast(:createdToExclusive as OffsetDateTime) is null
                        or paymentRequest.createdAt < :createdToExclusive)
                    """
    )
    Page<PaymentRequest> search(
            @Param("requestNo") String requestNo,
            @Param("approvalStatus") ApprovalStatus approvalStatus,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("requestCategory") RequestCategory requestCategory,
            @Param("applicantId") Long applicantId,
            @Param("departmentId") Long departmentId,
            @Param("companyId") Long companyId,
            @Param("customerId") Long customerId,
            @Param("createdFrom") OffsetDateTime createdFrom,
            @Param("createdToExclusive") OffsetDateTime createdToExclusive,
            Pageable pageable
    );
}
