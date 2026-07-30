package tw.com.jsgcpa.paymentapproval.master.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;

@Entity
@Table(name = "expense_price_settings")
public class ExpensePriceSetting extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "expense_type_id", nullable = false) private ExpenseType expenseType;
    @Column(name = "price_code", nullable = false, length = 50) private String priceCode = "DEFAULT";
    @Column(name = "price_name", nullable = false, length = 100) private String priceName;
    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2) private BigDecimal unitPrice;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "active", nullable = false) private Boolean active = true;
    public ExpensePriceSetting() {}
    public Long getId() { return id; }
    public ExpenseType getExpenseType() { return expenseType; } public void setExpenseType(ExpenseType v) { expenseType = v; }
    public String getPriceCode() { return priceCode; } public void setPriceCode(String v) { priceCode = v; }
    public String getPriceName() { return priceName; } public void setPriceName(String v) { priceName = v; }
    public BigDecimal getUnitPrice() { return unitPrice; } public void setUnitPrice(BigDecimal v) { unitPrice = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate v) { effectiveFrom = v; }
    public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate v) { effectiveTo = v; }
    public Boolean getActive() { return active; } public void setActive(Boolean v) { active = v; }
}
