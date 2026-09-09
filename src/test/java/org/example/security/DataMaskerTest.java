package org.example.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataMaskerTest {

    private DataMasker dataMasker;

    @BeforeEach
    void setUp() {
        dataMasker = new DataMasker();
    }

    @Test
    @DisplayName("Should mask passwords in JSON or key-value configs")
    void testMaskPasswords() {
        String input1 = "{\"username\": \"admin\", \"password\": \"SuperSecret123!\"}";
        String masked1 = dataMasker.mask(input1);
        assertFalse(masked1.contains("SuperSecret123!"));
        assertTrue(masked1.contains("***REDACTED***"));

        String input2 = "db.url=jdbc:mysql://localhost:3306/db?user=root&password=mypassword123";
        String masked2 = dataMasker.mask(input2);
        assertFalse(masked2.contains("mypassword123"));
        assertTrue(masked2.contains("***REDACTED***"));
    }

    @Test
    @DisplayName("Should mask Bearer tokens")
    void testMaskBearerTokens() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz123";
        String masked = dataMasker.mask(input);
        assertFalse(masked.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz123"));
        assertTrue(masked.contains("Bearer ***REDACTED_TOKEN***"));
    }

    @Test
    @DisplayName("Should mask private keys")
    void testMaskPrivateKeys() {
        String input = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA0...\n-----END RSA PRIVATE KEY-----";
        String masked = dataMasker.mask(input);
        assertFalse(masked.contains("MIIEowIBAAKCAQEA0..."));
        assertTrue(masked.contains("*** REDACTED SENSITIVE KEY ***"));
    }

    @Test
    @DisplayName("Should mask credentials inside URLs")
    void testMaskUrlCredentials() {
        String input = "Connecting to redis://root:secretPass@10.0.0.1:6379/0";
        String masked = dataMasker.mask(input);
        assertFalse(masked.contains("secretPass"));
        assertTrue(masked.contains("redis://root:***@10.0.0.1:6379/0"));
    }
}
