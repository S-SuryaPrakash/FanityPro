package com.example.contentfilter.account;

import com.example.contentfilter.audit.AuditAction;
import com.example.contentfilter.audit.AuditService;
import com.example.contentfilter.exception.ConflictException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and lists V2 accounts. Only administrators reach these operations. */
@Service
@Profile("v2")
public class UserAccountService {

	public static final int MIN_PASSWORD_LENGTH = 12;
	/** BCrypt ignores input beyond 72 bytes, so longer passwords are rejected. */
	public static final int MAX_PASSWORD_LENGTH = 72;

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final AuditService audit;
	private final Clock clock;

	public UserAccountService(
			AppUserRepository users,
			PasswordEncoder passwordEncoder,
			AuditService audit,
			Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * @param actorId the administrator creating the account, or {@code null}
	 *                for the system bootstrap
	 */
	@Transactional
	public UserView create(UUID actorId, String email, String password, UserRole role) {
		String normalizedEmail = AppUser.normalizeEmail(email);
		if (normalizedEmail == null || normalizedEmail.isEmpty()) {
			throw new IllegalArgumentException("An email address is required.");
		}
		if (role == null) {
			throw new IllegalArgumentException("A role is required.");
		}
		requireAcceptablePassword(password);
		if (users.existsByEmail(normalizedEmail)) {
			throw new ConflictException("EMAIL_ALREADY_REGISTERED",
					"An account with this email address already exists.");
		}

		Instant now = clock.instant();
		AppUser user = users.save(
				new AppUser(normalizedEmail, passwordEncoder.encode(password), role, now));
		audit.record(actorId, AuditAction.USER_CREATED, "USER", user.getId(),
				Map.of("role", role.name(), "bootstrap", actorId == null));
		return UserView.from(user);
	}

	@Transactional(readOnly = true)
	public Page<UserView> list(Pageable pageable) {
		return users.findAll(pageable).map(UserView::from);
	}

	@Transactional(readOnly = true)
	public boolean hasAnyAccount() {
		return users.count() > 0;
	}

	private static void requireAcceptablePassword(String password) {
		if (password == null
				|| password.length() < MIN_PASSWORD_LENGTH
				|| password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("A password must be " + MIN_PASSWORD_LENGTH
					+ " to " + MAX_PASSWORD_LENGTH + " characters long.");
		}
	}

	/** Public account view; the password hash never leaves the service. */
	public record UserView(
			UUID id,
			String email,
			UserRole role,
			boolean enabled,
			Instant createdAt,
			Instant lastLoginAt) {

		static UserView from(AppUser user) {
			return new UserView(user.getId(), user.getEmail(), user.getRole(), user.isEnabled(),
					user.getCreatedAt(), user.getLastLoginAt());
		}
	}
}
