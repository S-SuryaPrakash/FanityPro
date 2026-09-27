package com.example.contentfilter.security;

import com.example.contentfilter.account.AppUser;
import com.example.contentfilter.account.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Login-time view of an account; only lives for the password check. */
record AppUserPrincipal(UUID id, String email, String passwordHash, UserRole role, boolean enabled)
		implements UserDetails {

	static AppUserPrincipal from(AppUser user) {
		return new AppUserPrincipal(
				user.getId(), user.getEmail(), user.getPasswordHash(), user.getRole(), user.isEnabled());
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isEnabled() {
		return enabled;
	}

	@Override
	public String toString() {
		// Keep the hash out of logs and debugger dumps.
		return "AppUserPrincipal[id=" + id + ", role=" + role + ", enabled=" + enabled + "]";
	}
}
