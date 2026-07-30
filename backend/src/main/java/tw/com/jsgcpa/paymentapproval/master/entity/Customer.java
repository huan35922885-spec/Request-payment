package tw.com.jsgcpa.paymentapproval.master.entity;

import jakarta.persistence.*;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;
import tw.com.jsgcpa.paymentapproval.payment.enums.RequestCategory;

@Entity
@Table(name = "customers")
public class Customer extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "code", nullable = false, unique = true, length = 50) private String code;
    @Column(name = "name", nullable = false, length = 200) private String name;
    @Enumerated(EnumType.STRING) @Column(name = "default_request_category", length = 20) private RequestCategory defaultRequestCategory;
    @Column(name = "active", nullable = false) private Boolean active = true;
    public Customer() {}
    public Long getId() { return id; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public RequestCategory getDefaultRequestCategory() { return defaultRequestCategory; } public void setDefaultRequestCategory(RequestCategory v) { defaultRequestCategory = v; }
    public Boolean getActive() { return active; } public void setActive(Boolean v) { active = v; }
}
