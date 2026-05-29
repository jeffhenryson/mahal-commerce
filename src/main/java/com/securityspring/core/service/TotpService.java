package com.securityspring.core.service;

import com.securityspring.core.domain.exception.auth.InvalidPasswordException;
import com.securityspring.core.domain.exception.auth.InvalidTotpCodeException;
import com.securityspring.core.domain.exception.auth.TotpAlreadyEnabledException;
import com.securityspring.core.domain.exception.auth.TotpChallengeExpiredException;
import com.securityspring.core.domain.exception.auth.TotpNotEnabledException;
import com.securityspring.core.domain.model.auth.TotpChallengeToken;
import com.securityspring.core.domain.model.auth.TotpConfig;
import com.securityspring.core.ports.in.TotpUseCase;
import com.securityspring.core.ports.out.credential.PasswordHashPort;
import com.securityspring.core.ports.out.twofa.TotpBackupCodeRepository;
import com.securityspring.core.ports.out.twofa.TotpChallengeTokenRepository;
import com.securityspring.core.ports.out.twofa.TotpConfigRepository;
import com.securityspring.core.ports.out.twofa.TotpEncryptionPort;
import com.securityspring.core.ports.out.twofa.TwoFactorAuthPort;
import com.securityspring.core.ports.out.user.UserRepository;
import com.securityspring.core.domain.exception.user.UserNotFoundException;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.secret.SecretGenerator;

import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class TotpService implements TotpUseCase, TwoFactorAuthPort {

    private static final int BACKUP_CODE_COUNT = 8;
    private static final int CHALLENGE_TTL_MINUTES = 5;

    private final TotpConfigRepository totpConfigRepository;
    private final TotpBackupCodeRepository totpBackupCodeRepository;
    private final TotpChallengeTokenRepository totpChallengeTokenRepository;
    private final TotpEncryptionPort encryptionPort;
    private final PasswordHashPort passwordHashPort;
    private final UserRepository userRepository;
    private final SecretGenerator secretGenerator;
    private final CodeVerifier codeVerifier;
    private final String appName;
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpService(TotpConfigRepository totpConfigRepository,
            TotpBackupCodeRepository totpBackupCodeRepository,
            TotpChallengeTokenRepository totpChallengeTokenRepository,
            TotpEncryptionPort encryptionPort,
            PasswordHashPort passwordHashPort,
            UserRepository userRepository,
            SecretGenerator secretGenerator,
            CodeVerifier codeVerifier,
            String appName) {
        this.totpConfigRepository = totpConfigRepository;
        this.totpBackupCodeRepository = totpBackupCodeRepository;
        this.totpChallengeTokenRepository = totpChallengeTokenRepository;
        this.encryptionPort = encryptionPort;
        this.passwordHashPort = passwordHashPort;
        this.userRepository = userRepository;
        this.secretGenerator = secretGenerator;
        this.codeVerifier = codeVerifier;
        this.appName = appName;
    }

    @Override
    @Transactional
    public TotpSetupResult setup(String username) {
        totpConfigRepository.findByUsername(username).ifPresent(cfg -> {
            if (cfg.enabled()) throw new TotpAlreadyEnabledException();
        });
        // Regenerate if pending (not yet confirmed)
        totpConfigRepository.deleteByUsername(username);
        totpBackupCodeRepository.deleteByUsername(username);

        String secret = secretGenerator.generate();
        String secretEncrypted = encryptionPort.encrypt(secret);
        totpConfigRepository.save(username, secretEncrypted);

        String otpauthUri = "otpauth://totp/" + encode(appName) + ":" + encode(username)
                + "?secret=" + secret + "&issuer=" + encode(appName) + "&algorithm=SHA1&digits=6&period=30";

        return new TotpSetupResult(secret, otpauthUri);
    }

    @Override
    @Transactional
    public List<String> confirm(String username, String totpCode) {
        TotpConfig config = totpConfigRepository.findByUsername(username)
                .orElseThrow(TotpNotEnabledException::new);

        if (config.enabled()) throw new TotpAlreadyEnabledException();

        String secret = encryptionPort.decrypt(config.secretEncrypted());
        if (!codeVerifier.isValidCode(secret, totpCode)) {
            throw new InvalidTotpCodeException();
        }

        totpConfigRepository.enable(username, Instant.now());

        List<String> backupCodes = generateBackupCodes();
        totpBackupCodeRepository.deleteByUsername(username);
        totpBackupCodeRepository.saveAll(username, backupCodes);

        return backupCodes;
    }

    @Override
    @Transactional
    public void disable(String username, String currentPassword, String totpCode) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (!passwordHashPort.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }

        TotpConfig config = totpConfigRepository.findByUsername(username)
                .orElseThrow(TotpNotEnabledException::new);
        if (!config.enabled()) throw new TotpNotEnabledException();

        String secret = encryptionPort.decrypt(config.secretEncrypted());
        boolean validTotp = codeVerifier.isValidCode(secret, totpCode);
        boolean validBackup = !validTotp && isValidBackupCode(username, totpCode);

        if (!validTotp && !validBackup) {
            throw new InvalidTotpCodeException();
        }

        totpConfigRepository.deleteByUsername(username);
        totpBackupCodeRepository.deleteByUsername(username);
    }

    @Override
    @Transactional
    public List<String> regenerateBackupCodes(String username, String currentPassword) {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (!passwordHashPort.matches(currentPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }

        TotpConfig config = totpConfigRepository.findByUsername(username)
                .orElseThrow(TotpNotEnabledException::new);
        if (!config.enabled()) throw new TotpNotEnabledException();

        List<String> backupCodes = generateBackupCodes();
        totpBackupCodeRepository.deleteByUsername(username);
        totpBackupCodeRepository.saveAll(username, backupCodes);
        return backupCodes;
    }

    // --- TwoFactorAuthPort ---

    @Override
    @Transactional(readOnly = true)
    public boolean isEnabled(String username) {
        return totpConfigRepository.findByUsername(username)
                .map(TotpConfig::enabled)
                .orElse(false);
    }

    @Override
    @Transactional
    public String issueChallengeToken(String username) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(CHALLENGE_TTL_MINUTES, ChronoUnit.MINUTES);
        totpChallengeTokenRepository.save(username, rawToken, expiresAt);
        return rawToken;
    }

    /** Verifica challenge token + código TOTP (ou backup code). Retorna username se válido. */
    @Transactional
    public String completeChallengeLogin(String rawChallengeToken, String totpCode) {
        TotpChallengeToken challenge = totpChallengeTokenRepository.findByToken(rawChallengeToken)
                .orElseThrow(TotpChallengeExpiredException::new);

        if (challenge.isExpired() || challenge.isUsed()) throw new TotpChallengeExpiredException();

        if (!totpChallengeTokenRepository.markAsUsed(rawChallengeToken)) {
            throw new TotpChallengeExpiredException();
        }

        String username = challenge.username();
        TotpConfig config = totpConfigRepository.findByUsername(username)
                .orElseThrow(TotpNotEnabledException::new);

        String secret = encryptionPort.decrypt(config.secretEncrypted());
        boolean validTotp = codeVerifier.isValidCode(secret, totpCode);
        boolean validBackup = !validTotp && isValidBackupCode(username, totpCode);

        if (!validTotp && !validBackup) {
            throw new InvalidTotpCodeException();
        }

        return username;
    }

    private boolean isValidBackupCode(String username, String code) {
        return totpBackupCodeRepository.findByCode(code)
                .filter(b -> b.username().equals(username) && !b.isUsed())
                .map(b -> totpBackupCodeRepository.markAsUsed(code))
                .orElse(false);
    }

    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            // Formato: XXXX-XXXX-XXXX (12 chars alfanuméricos agrupados para legibilidade)
            byte[] b = new byte[6];
            secureRandom.nextBytes(b);
            String hex = bytesToAlpha(b);
            codes.add(hex.substring(0, 4) + "-" + hex.substring(4, 8) + "-" + hex.substring(8, 12));
        }
        return codes;
    }

    private static String bytesToAlpha(byte[] bytes) {
        final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(ALPHABET.charAt(Math.abs(b % ALPHABET.length())));
            sb.append(ALPHABET.charAt(Math.abs((b >> 4) % ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
