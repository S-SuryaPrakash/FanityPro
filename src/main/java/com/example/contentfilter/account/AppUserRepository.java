package com.example.contentfilter.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	/** Expects an already-normalized email; see {@link AppUser#normalizeEmail}. */
	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);
}
