package tw.com.jsgcpa.paymentapproval.master.entity;

import jakarta.persistence.*;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;
import tw.com.jsgcpa.paymentapproval.master.enums.CalculationType;

@Entity
@Table(name = "expense_types")
public class ExpenseType extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "code", nullable = false, unique = true, length = 50) private String code;
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "calculation_type", nullable = false, length = 30) private CalculationType calculationType;
    @Column(name = "active", nullable = false) private Boolean active = true;
    public ExpenseType() {}
    public Long getId() { return id; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public CalculationType getCalculationType() { return calculationType; } public void setCalculationType(CalculationType v) { calculationType = v; }
    public Boolean getActive() { return active; } public void setActive(Boolean v) { active = v; }
}
