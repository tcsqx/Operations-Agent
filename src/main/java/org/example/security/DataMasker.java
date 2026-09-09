package org.example.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class DataMasker {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "(?i)(\"?(password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|sk|ak)\"?\\s*[:=]\\s*\"?)([^\"'\\s,;&]+)(\"?)");

    private static final Pattern BEARER_PATTERN = Pattern.compile(
        "(?i)bearer\\s+[a-zA-Z0-9_\\-\\.]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
        "-----BEGIN [A-Z ]+ PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]+ PRIVATE KEY-----");

    private static final Pattern IPV4_AUTH_URL = Pattern.compile(
        "://([^:]+):([^@]+)@");

    /**
     * Masks sensitive tokens, passwords, private keys, and authorization headers in text.
     */
    public String mask(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        String result = input;

        // Mask private keys
        result = PRIVATE_KEY_PATTERN.matcher(result).replaceAll("-----BEGIN PRIVATE KEY-----\\n*** REDACTED SENSITIVE KEY ***\\n-----END PRIVATE KEY-----");

        // Mask bearer tokens
        result = BEARER_PATTERN.matcher(result).replaceAll("Bearer ***REDACTED_TOKEN***");

        // Mask credentials in URLs: http://user:pass@host -> http://user:***@host
        result = IPV4_AUTH_URL.matcher(result).replaceAll("://$1:***@");

        // Mask key-value credentials: password=xyz -> password=***
        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1***REDACTED***$4");

        return result;
    }
}
