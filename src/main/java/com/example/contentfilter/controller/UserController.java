package com.example.contentfilter.controller;

import com.example.contentfilter.account.UserAccountService;
import com.example.contentfilter.account.UserAccountService.UserView;
import com.example.contentfilter.account.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Account administration; restricted to ADMIN in {@code V2SecurityConfiguration}. */
@RestController
@Profile("v2")
@RequestMapping("/api/v2/users")
public class UserController {

	private final UserAccountService accounts;

	public UserController(UserAccountService accounts) {
		this.accounts = accounts;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserView create(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateUserRequest request) {
		return accounts.create(
				UUID.fromString(jwt.getSubject()), request.email(), request.password(), request.role());
	}

	@GetMapping
	public PageResponse<UserView> list(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return PageResponse.from(accounts.list(
				PageRequest.of(page, size, Sort.by("createdAt", "id"))));
	}

	public record CreateUserRequest(
			@NotBlank @Email @Size(max = 320) String email,
			@NotBlank
			@Size(min = UserAccountService.MIN_PASSWORD_LENGTH,
					max = UserAccountService.MAX_PASSWORD_LENGTH)
			String password,
			@NotNull UserRole role) {

		@Override
		public String toString() {
			return "CreateUserRequest[email=" + email + ", password=<redacted>, role=" + role + "]";
		}
	}
}
