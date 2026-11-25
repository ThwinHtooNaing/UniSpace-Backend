package com.unispace.administration.persistence;

import com.unispace.administration.application.InstitutionalRegistrationPolicy;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class InstitutionalRegistrationPolicyReader implements InstitutionalRegistrationPolicy {
    private static final JsonMapper JSON = new JsonMapper();
    private final JdbcTemplate jdbc;

    public InstitutionalRegistrationPolicyReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Rules> current() {
        var rules = jdbc.query("""
                SELECT student_id_pattern, allowed_email_domains FROM institutional_policies LIMIT 2 FOR SHARE
                """, (rows, index) -> parse(rows.getString(1), rows.getString(2)));
        return rules.size() == 1 ? rules.getFirst() : Optional.empty();
    }

    private Optional<Rules> parse(String studentIdPattern, String domains) {
        try {
            var values = JSON.readTree(domains);
            if (!values.isArray() || values.isEmpty() || studentIdPattern.isBlank()) return Optional.empty();
            var allowed = new HashSet<String>();
            for (var value : values) {
                if (!value.isString()) return Optional.empty();
                String domain = value.asString().strip().toLowerCase(Locale.ROOT);
                if (domain.isEmpty()) return Optional.empty();
                allowed.add(domain);
            }
            return Optional.of(new Rules(Pattern.compile(studentIdPattern), Set.copyOf(allowed)));
        } catch (JacksonException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
