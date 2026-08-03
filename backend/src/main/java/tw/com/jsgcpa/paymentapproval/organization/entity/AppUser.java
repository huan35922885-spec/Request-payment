package tw.com.jsgcpa.paymentapproval.organization.entity;

import jakarta.persistence.*;
import tw.com.jsgcpa.paymentapproval.security.entity.AppUserCredential;
import tw.com.jsgcpa.paymentapproval.security.entity.AppUserRole;
import tw.com.jsgcpa.paymentapproval.common.entity.BaseTimeEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "app_users")
public class AppUser extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "username", nullable = false, unique = true, length = 100) private String username;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "email", length = 255) private String email;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "department_id") private Department department;
    @Column(name = "active", nullable = false) private Boolean active = true;
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY) private AppUserCredential credential;
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY) private List<AppUserRole> roles = new ArrayList<>();
    public AppUser() {}
    public Long getId() { return id; }
    public String getUsername() { return username; } public void setUsername(String v) { username = v; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String v) { displayName = v; }
    public String getEmail() { return email; } public void setEmail(String v) { email = v; }
    public Department getDepartment() { return department; } public void setDepartment(Department v) { department = v; }
    public Boolean getActive() { return active; } public void setActive(Boolean v) { active = v; }
    public AppUserCredential getCredential() { return credential; } public void setCredential(AppUserCredential v) { credential = v; }
    public List<AppUserRole> getRoles() { return roles; } public void setRoles(List<AppUserRole> v) { roles = v; }
}
