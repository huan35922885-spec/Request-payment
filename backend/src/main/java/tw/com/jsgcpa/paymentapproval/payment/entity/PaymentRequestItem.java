package tw.com.jsgcpa.paymentapproval.payment.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpensePriceSetting;
import tw.com.jsgcpa.paymentapproval.master.entity.ExpenseType;

@Entity
@Table(name = "payment_request_items")
public class PaymentRequestItem extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_request_id", nullable = false) private PaymentRequest paymentRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "expense_type_id", nullable = false) private ExpenseType expenseType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "price_setting_id") private ExpensePriceSetting priceSetting;
    @Column(name = "description", columnDefinition = "TEXT") private String description;
    @Column(name = "people_count") private Integer peopleCount;
    @Column(name = "days") private Integer days;
    @Column(name = "quantity", precision = 14, scale = 2) private BigDecimal quantity;
    @Column(name = "unit_price", precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "multiplier", nullable = false, precision = 10, scale = 2) private BigDecimal multiplier = BigDecimal.ONE;
    @Column(name = "amount", nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "extra_data", nullable = false, columnDefinition = "jsonb") private Map<String, Object> extraData = new LinkedHashMap<>();
    @Column(name = "sort_order", nullable = false) private Integer sortOrder = 1;
    public PaymentRequestItem() {}
    public Long getId() { return id; }
    public PaymentRequest getPaymentRequest() { return paymentRequest; } public void setPaymentRequest(PaymentRequest v) { paymentRequest = v; }
    public ExpenseType getExpenseType() { return expenseType; } public void setExpenseType(ExpenseType v) { expenseType = v; }
    public ExpensePriceSetting getPriceSetting() { return priceSetting; } public void setPriceSetting(ExpensePriceSetting v) { priceSetting = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public Integer getPeopleCount() { return peopleCount; } public void setPeopleCount(Integer v) { peopleCount = v; }
    public Integer getDays() { return days; } public void setDays(Integer v) { days = v; }
    public BigDecimal getQuantity() { return quantity; } public void setQuantity(BigDecimal v) { quantity = v; }
    public BigDecimal getUnitPrice() { return unitPrice; } public void setUnitPrice(BigDecimal v) { unitPrice = v; }
    public BigDecimal getMultiplier() { return multiplier; } public void setMultiplier(BigDecimal v) { multiplier = v; }
    public BigDecimal getAmount() { return amount; } public void setAmount(BigDecimal v) { amount = v; }
    public Map<String, Object> getExtraData() { return extraData; } public void setExtraData(Map<String, Object> v) { extraData = v; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer v) { sortOrder = v; }
}
