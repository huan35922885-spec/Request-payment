package tw.com.jsgcpa.paymentapproval.organization.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;

@Entity
@Table(name = "department_supervisors")
public class DepartmentSupervisor extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "department_id", nullable = false) private Department department;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supervisor_id", nullable = false) private AppUser supervisor;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "active", nullable = false) private Boolean active = true;
    public DepartmentSupervisor() {}
    public Long getId() { return id; }
    public Department getDepartment() { return department; } public void setDepartment(Department v) { department = v; }
    public AppUser getSupervisor() { return supervisor; } public void setSupervisor(AppUser v) { supervisor = v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; } public void setEffectiveFrom(LocalDate v) { effectiveFrom = v; }
    public LocalDate getEffectiveTo() { return effectiveTo; } public void setEffectiveTo(LocalDate v) { effectiveTo = v; }
    public Boolean getActive() { return active; } public void setActive(Boolean v) { active = v; }
}
