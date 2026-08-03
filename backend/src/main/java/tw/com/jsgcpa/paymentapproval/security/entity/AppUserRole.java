package tw.com.jsgcpa.paymentapproval.security.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Index;
import tw.com.jsgcpa.paymentapproval.organization.entity.AppUser;
import tw.com.jsgcpa.paymentapproval.security.enums.SecurityRole;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "app_user_roles",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_app_user_roles_user_role",
        columnNames = {"user_id", "role_code"}
    ),
    indexes = {
        @Index(name = "idx_app_user_roles_user_id", columnList = "user_id"),
        @Index(name = "idx_app_user_roles_role_code", columnList = "role_code")
    }
)
public class AppUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 50)
    private SecurityRole roleCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public AppUserRole() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public SecurityRole getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(SecurityRole roleCode) {
        this.roleCode = roleCode;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
