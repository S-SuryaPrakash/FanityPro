package com.example.contentfilter.security;

import com.example.contentfilter.account.UserAccountService;
import com.example.contentfilter.account.UserRole;
import com.example.contentfilter.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator from configuration so a fresh database has
 * a way in. Does nothing once any account exists, so rotating or removing the
 * bootstrap settings after first start is safe.
 */
@Component
@Profile("v2")
class BootstrapAdminInitializer implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

	private final UserAccountService accounts;
	private final SecurityProperties.BootstrapAdmin settings;

	BootstrapAdminInitializer(UserAccountService accounts, SecurityProperties properties) {
		this.accounts = accounts;
		this.settings = properties.bootstrapAdmin();
	}

	@Override
	public void run(ApplicationArguments args) {
		if (accounts.hasAnyAccount()) {
			return;
		}
		if (!settings.isConfigured()) {
			LOGGER.warn("No V2 accounts exist and no bootstrap admin is configured; "
					+ "set CONTENT_FILTER_BOOTSTRAP_ADMIN_EMAIL and _PASSWORD to create one.");
			return;
		}
		try {
			accounts.create(null, settings.email(), settings.password(), UserRole.ADMIN);
			LOGGER.info("Created the bootstrap administrator account.");
		} catch (ConflictException | DataIntegrityViolationException exception) {
			// Another instance created it first.
			LOGGER.info("Bootstrap administrator already exists.");
		}
	}
}
