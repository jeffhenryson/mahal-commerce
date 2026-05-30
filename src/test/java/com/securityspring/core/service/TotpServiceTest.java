package com.securityspring.core.service;

import com.securityspring.core.domain.exception.auth.InvalidPasswordException;
import com.securityspring.core.domain.exception.auth.InvalidTotpCodeException;
import com.securityspring.core.domain.exception.auth.TotpAlreadyEnabledException;
import com.securityspring.core.domain.exception.auth.TotpChallengeExpiredException;
import com.securityspring.core.domain.exception.auth.TotpNotEnabledException;
import com.securityspring.core.domain.model.auth.TotpBackupCode;
import com.securityspring.core.domain.model.auth.TotpChallengeToken;
import com.securityspring.core.domain.model.auth.TotpConfig;
import com.securityspring.core.domain.model.auth.User;
import com.securityspring.core.ports.out.credential.PasswordHashPort;
import com.securityspring.core.ports.out.twofa.TotpBackupCodeRepository;
import com.securityspring.core.ports.out.twofa.TotpChallengeTokenRepository;
import com.securityspring.core.ports.out.twofa.TotpConfigRepository;
import com.securityspring.core.ports.out.twofa.TotpEncryptionPort;
import com.securityspring.core.ports.out.user.UserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.secret.SecretGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TotpServiceTest {

    @Mock TotpConfigRepository totpConfigRepository;
    @Mock TotpBackupCodeRepository totpBackupCodeRepository;
    @Mock TotpChallengeTokenRepository totpChallengeTokenRepository;
    @Mock TotpEncryptionPort encryptionPort;
    @Mock PasswordHashPort passwordHashPort;
    @Mock UserRepository userRepository;
    @Mock SecretGenerator secretGenerator;
    @Mock CodeVerifier codeVerifier;

    TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService(totpConfigRepository, totpBackupCodeRepository,
                totpChallengeTokenRepository, encryptionPort, passwordHashPort,
                userRepository, secretGenerator, codeVerifier, "TestApp");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private TotpConfig configEnabled() {
        return new TotpConfig(1L, "alice", "ENCRYPTED_SECRET", true, Instant.now());
    }

    private TotpConfig configPending() {
        return new TotpConfig(1L, "alice", "ENCRYPTED_SECRET", false, null);
    }

    private TotpChallengeToken validChallenge() {
        return new TotpChallengeToken(1L, "alice", "HASH",
                Instant.now().plusSeconds(300), Instant.now(), null);
    }

    private TotpChallengeToken expiredChallenge() {
        return new TotpChallengeToken(1L, "alice", "HASH",
                Instant.now().minusSeconds(1), Instant.now(), null);
    }

    private TotpChallengeToken usedChallenge() {
        return new TotpChallengeToken(1L, "alice", "HASH",
                Instant.now().plusSeconds(300), Instant.now(), Instant.now());
    }

    // ── isEnabled ─────────────────────────────────────────────────────────────

    @Test
    void isEnabled_returnsTrueWhenConfigEnabled() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));
        assertThat(totpService.isEnabled("alice")).isTrue();
    }

    @Test
    void isEnabled_returnsFalseWhenNoPendingConfig() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.empty());
        assertThat(totpService.isEnabled("alice")).isFalse();
    }

    @Test
    void isEnabled_returnsFalseWhenConfigNotYetConfirmed() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configPending()));
        assertThat(totpService.isEnabled("alice")).isFalse();
    }

    // ── setup ─────────────────────────────────────────────────────────────────

    @Test
    void setup_throwsWhenAlreadyEnabled() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));

        assertThatThrownBy(() -> totpService.setup("alice"))
                .isInstanceOf(TotpAlreadyEnabledException.class);
    }

    @Test
    void setup_generatesSecretAndReturnsUri() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.empty());
        when(secretGenerator.generate()).thenReturn("BASE32SECRET");
        when(encryptionPort.encrypt("BASE32SECRET")).thenReturn("ENCRYPTED");

        TotpService.TotpSetupResult result = totpService.setup("alice");

        assertThat(result.secret()).isEqualTo("BASE32SECRET");
        assertThat(result.otpauthUri()).contains("BASE32SECRET");
        assertThat(result.otpauthUri()).contains("TestApp");
        assertThat(result.otpauthUri()).contains("alice");
        verify(totpConfigRepository).save("alice", "ENCRYPTED");
    }

    @Test
    void setup_deletesExistingPendingConfigAndRegenerates() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configPending()));
        when(secretGenerator.generate()).thenReturn("NEW_SECRET");
        when(encryptionPort.encrypt("NEW_SECRET")).thenReturn("NEW_ENCRYPTED");

        totpService.setup("alice");

        verify(totpConfigRepository).deleteByUsername("alice");
        verify(totpBackupCodeRepository).deleteByUsername("alice");
        verify(totpConfigRepository).save("alice", "NEW_ENCRYPTED");
    }

    // ── confirm ───────────────────────────────────────────────────────────────

    @Test
    void confirm_throwsWhenNoPendingConfig() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> totpService.confirm("alice", "123456"))
                .isInstanceOf(TotpNotEnabledException.class);
    }

    @Test
    void confirm_throwsWhenAlreadyEnabled() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));

        assertThatThrownBy(() -> totpService.confirm("alice", "123456"))
                .isInstanceOf(TotpAlreadyEnabledException.class);
    }

    @Test
    void confirm_throwsWhenInvalidCode() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configPending()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> totpService.confirm("alice", "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test
    void confirm_enablesAndReturnsEightBackupCodes() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configPending()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "123456")).thenReturn(true);

        List<String> codes = totpService.confirm("alice", "123456");

        assertThat(codes).hasSize(8);
        assertThat(codes).allMatch(c -> c.matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"));
        verify(totpConfigRepository).enable(eq("alice"), any(Instant.class));
        verify(totpBackupCodeRepository).saveAll(eq("alice"), eq(codes));
    }

    // ── disable ───────────────────────────────────────────────────────────────

    @Test
    void disable_throwsWhenPasswordInvalid() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHashPort.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> totpService.disable("alice", "wrong", "123456"))
                .isInstanceOf(InvalidPasswordException.class);
        verifyNoInteractions(totpConfigRepository);
    }

    @Test
    void disable_throwsWhenNotEnabled() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHashPort.matches("correct", "hashed")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> totpService.disable("alice", "correct", "123456"))
                .isInstanceOf(TotpNotEnabledException.class);
    }

    @Test
    void disable_withValidTotp_deletesConfigAndCodes() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHashPort.matches("correct", "hashed")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "123456")).thenReturn(true);

        totpService.disable("alice", "correct", "123456");

        verify(totpConfigRepository).deleteByUsername("alice");
        verify(totpBackupCodeRepository).deleteByUsername("alice");
    }

    @Test
    void disable_withValidBackupCode_succeeds() {
        User user = User.of("alice", "hashed", null);
        TotpBackupCode backupCode = new TotpBackupCode(1L, "alice", "HASH", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHashPort.matches("correct", "hashed")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "AAAA-BBBB-CCCC")).thenReturn(false);
        when(totpBackupCodeRepository.findByCode("AAAA-BBBB-CCCC")).thenReturn(Optional.of(backupCode));
        when(totpBackupCodeRepository.markAsUsed("AAAA-BBBB-CCCC")).thenReturn(true);

        totpService.disable("alice", "correct", "AAAA-BBBB-CCCC");

        verify(totpConfigRepository).deleteByUsername("alice");
    }

    @Test
    void disable_throwsWhenBothTotpAndBackupInvalid() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHashPort.matches("correct", "hashed")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "999999")).thenReturn(false);
        when(totpBackupCodeRepository.findByCode("999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> totpService.disable("alice", "correct", "999999"))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    // ── regenerateBackupCodes ─────────────────────────────────────────────────

    @Test
    void regenerateBackupCodes_throwsWhenPasswordInvalid() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHashPort.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> totpService.regenerateBackupCodes("alice", "wrong"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void regenerateBackupCodes_throwsWhenNotEnabled() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHashPort.matches("correct", "hashed")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> totpService.regenerateBackupCodes("alice", "correct"))
                .isInstanceOf(TotpNotEnabledException.class);
    }

    @Test
    void regenerateBackupCodes_deletesOldAndSavesNewCodes() {
        User user = User.of("alice", "hashed", null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHashPort.matches("correct", "hashed")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));

        List<String> codes = totpService.regenerateBackupCodes("alice", "correct");

        assertThat(codes).hasSize(8);
        assertThat(codes).allMatch(c -> c.matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"));
        verify(totpBackupCodeRepository).deleteByUsername("alice");
        verify(totpBackupCodeRepository).saveAll(eq("alice"), eq(codes));
    }

    // ── completeChallengeLogin ─────────────────────────────────────────────────

    @Test
    void completeChallengeLogin_throwsWhenTokenNotFound() {
        when(totpChallengeTokenRepository.findByToken("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> totpService.completeChallengeLogin("INVALID", "123456"))
                .isInstanceOf(TotpChallengeExpiredException.class);
    }

    @Test
    void completeChallengeLogin_throwsWhenTokenExpired() {
        when(totpChallengeTokenRepository.findByToken("TOKEN")).thenReturn(Optional.of(expiredChallenge()));

        assertThatThrownBy(() -> totpService.completeChallengeLogin("TOKEN", "123456"))
                .isInstanceOf(TotpChallengeExpiredException.class);
    }

    @Test
    void completeChallengeLogin_throwsWhenTokenAlreadyUsed() {
        when(totpChallengeTokenRepository.findByToken("TOKEN")).thenReturn(Optional.of(usedChallenge()));

        assertThatThrownBy(() -> totpService.completeChallengeLogin("TOKEN", "123456"))
                .isInstanceOf(TotpChallengeExpiredException.class);
    }

    @Test
    void completeChallengeLogin_throwsWhenCASMarkAsUsedFails() {
        when(totpChallengeTokenRepository.findByToken("TOKEN")).thenReturn(Optional.of(validChallenge()));
        when(totpChallengeTokenRepository.markAsUsed("TOKEN")).thenReturn(false);

        assertThatThrownBy(() -> totpService.completeChallengeLogin("TOKEN", "123456"))
                .isInstanceOf(TotpChallengeExpiredException.class);
    }

    @Test
    void completeChallengeLogin_throwsWhenInvalidTotpCode() {
        when(totpChallengeTokenRepository.findByToken("TOKEN")).thenReturn(Optional.of(validChallenge()));
        when(totpChallengeTokenRepository.markAsUsed("TOKEN")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "000000")).thenReturn(false);
        when(totpBackupCodeRepository.findByCode("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> totpService.completeChallengeLogin("TOKEN", "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test
    void completeChallengeLogin_returnsUsernameOnSuccessWithTotp() {
        when(totpChallengeTokenRepository.findByToken("TOKEN")).thenReturn(Optional.of(validChallenge()));
        when(totpChallengeTokenRepository.markAsUsed("TOKEN")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "123456")).thenReturn(true);

        String username = totpService.completeChallengeLogin("TOKEN", "123456");

        assertThat(username).isEqualTo("alice");
    }

    @Test
    void completeChallengeLogin_returnsUsernameOnSuccessWithBackupCode() {
        TotpBackupCode backupCode = new TotpBackupCode(1L, "alice", "HASH", null);
        when(totpChallengeTokenRepository.findByToken("TOKEN")).thenReturn(Optional.of(validChallenge()));
        when(totpChallengeTokenRepository.markAsUsed("TOKEN")).thenReturn(true);
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configEnabled()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "AAAA-BBBB-CCCC")).thenReturn(false);
        when(totpBackupCodeRepository.findByCode("AAAA-BBBB-CCCC")).thenReturn(Optional.of(backupCode));
        when(totpBackupCodeRepository.markAsUsed("AAAA-BBBB-CCCC")).thenReturn(true);

        String username = totpService.completeChallengeLogin("TOKEN", "AAAA-BBBB-CCCC");

        assertThat(username).isEqualTo("alice");
    }

    // ── backup code entropy ───────────────────────────────────────────────────

    @Test
    void confirm_backupCodes_formatAndFullAlphabetEntropy() {
        when(totpConfigRepository.findByUsername("alice")).thenReturn(Optional.of(configPending()));
        when(encryptionPort.decrypt("ENCRYPTED_SECRET")).thenReturn("SECRET");
        when(codeVerifier.isValidCode("SECRET", "123456")).thenReturn(true);

        List<String> codes = totpService.confirm("alice", "123456");

        // Formato: XXXX-XXXX-XXXX com chars do alfabeto [A-Z0-9]
        assertThat(codes).hasSize(8);
        assertThat(codes).allMatch(c -> c.matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"));

        // Regressão: b>>4 colapsava alternadas para apenas 9 chars (A-I).
        // Com Byte.toUnsignedInt(), todos os 12 chars são independentes do alfabeto completo (36).
        // 8 códigos × 12 chars = 96 amostras: probabilidade de cobrir ≤9 chars distintos é ~0.
        long distinctChars = codes.stream()
                .flatMapToInt(c -> c.replace("-", "").chars())
                .distinct()
                .count();
        assertThat(distinctChars).isGreaterThan(9);
    }
}
