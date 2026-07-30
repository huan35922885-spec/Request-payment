package tw.com.jsgcpa.paymentapproval.organization.entity;

import jakarta.persistence.*;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;

@Entity
@Table(name = "departments")
public class Department extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "code", nullable = false, unique = true, length = 50) private String code;
    @Column(name = "name", nullable = false, length = 100) private String name;
    @Column(name = "active", nullable = false) private Boolean active = true;
    public Department() {}
    public Long getId() { return id; }
    public String getCode() { return code; } public void setCode(String v) { code = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public Boolean getActive() { return active; } public void setActive(Boolean v) { active = v; }
}
