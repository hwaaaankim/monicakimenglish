package com.dev.MonicaKimEnglish.config;

import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AppPrincipal implements UserDetails {
    private final Long accountId;
    private final String username;
    private final String password;
    private final String displayName;
    private final String role;
    private final boolean enabled;

    public AppPrincipal(Long accountId, String username, String password, String displayName, String role, boolean enabled) {
        this.accountId = accountId;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.role = role;
        this.enabled = enabled;
    }

    public Long getAccountId() { return accountId; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    @Override public String getUsername() { return username; }
    @Override public String getPassword() { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
