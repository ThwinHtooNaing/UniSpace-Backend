package com.unispace.administration.application;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public interface InstitutionalRegistrationPolicy {
    record Rules(Pattern studentIdPattern, Set<String> emailDomains) {}

    Optional<Rules> current();
}
