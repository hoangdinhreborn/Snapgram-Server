package com.example.auth.repository;

import com.example.auth.entity.AuthUser;
import com.example.auth.entity.Role;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<AuthUser> filterBy(String q, String status, String role, List<UUID> roleUserIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Search keyword (q) on username, email, displayName
            if (q != null && !q.isBlank()) {
                String trimmed = q.trim();
                if (trimmed.length() >= 2) {
                    String pattern = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
                    Predicate usernameMatch = cb.like(cb.lower(root.get("username")), pattern);
                    Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);
                    Predicate displayNameMatch = cb.like(cb.lower(root.get("displayName")), pattern);
                    predicates.add(cb.or(usernameMatch, emailMatch, displayNameMatch));
                }
            }

            // 2. Status filter
            if (status != null && !status.isBlank()) {
                String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
                switch (normalizedStatus) {
                    case "ACTIVE" -> predicates.add(cb.and(
                            cb.isFalse(root.get("banned")),
                            cb.isTrue(root.get("emailVerified"))
                    ));
                    case "BANNED" -> predicates.add(cb.isTrue(root.get("banned")));
                    case "PENDING" -> predicates.add(cb.and(
                            cb.isFalse(root.get("banned")),
                            cb.isFalse(root.get("emailVerified"))
                    ));
                    default -> throw new IllegalArgumentException(
                            String.format("Invalid status '%s'. Allowed values: [ACTIVE, BANNED, PENDING]", status)
                    );
                }
            }

            // 3. Role filter (matches primary enum role OR dynamic assigned role in auth_user_roles)
            if (role != null && !role.isBlank()) {
                String normalizedRole = role.trim().toUpperCase(Locale.ROOT);
                Role enumRole = null;
                try {
                    enumRole = Role.valueOf(normalizedRole);
                } catch (IllegalArgumentException ignored) {
                    // Not a primary enum role
                }

                Predicate primaryRoleMatch = (enumRole != null)
                        ? cb.equal(root.get("role"), enumRole)
                        : null;

                Predicate dynamicRoleMatch = (roleUserIds != null && !roleUserIds.isEmpty())
                        ? root.get("id").in(roleUserIds)
                        : null;

                if (primaryRoleMatch != null && dynamicRoleMatch != null) {
                    predicates.add(cb.or(primaryRoleMatch, dynamicRoleMatch));
                } else if (primaryRoleMatch != null) {
                    predicates.add(primaryRoleMatch);
                } else if (dynamicRoleMatch != null) {
                    predicates.add(dynamicRoleMatch);
                } else {
                    // Role neither exists in primary enum nor assigned to any user
                    predicates.add(cb.disjunction());
                }
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
