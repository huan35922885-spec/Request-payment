package tw.com.jsgcpa.paymentapproval.organization.entity;

import jakarta.persistence.*;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "username", nullable = false, unique = true, length = 100) private String username;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "email", length = 255) private String email;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "department_id") private Department department;
    @Column(name = "active", nullable = false) private Boolean active = true;
    public AppUser() {}
    public Long getId() { return id; }
    public String getUsername() { return username; } public void setUsername(String v) { username = v; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String v) { displayName = v; }
    public String getEmail() { return email; } public void setEmail(String v) { email = v; }
    public Department getDepartment() { return department; } public void setDepartment(Department v) { department = v; }
    public Boolean getActive() { return active; } public void setActive(Boolean v) { active = v; }
}
