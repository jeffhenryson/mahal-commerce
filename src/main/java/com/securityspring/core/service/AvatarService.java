package com.securityspring.core.service;

import com.securityspring.core.domain.exception.avatar.AvatarTooLargeException;
import com.securityspring.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.securityspring.core.domain.exception.user.UserNotFoundException;
import com.securityspring.core.domain.model.auth.User;
import com.securityspring.core.ports.in.AvatarUseCase;
import com.securityspring.core.ports.out.storage.AvatarStoragePort;
import com.securityspring.core.ports.out.user.UserCachePort;
import com.securityspring.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

public class AvatarService implements AvatarUseCase {

    private final UserRepository userRepository;
    private final AvatarStoragePort storagePort;
    private final UserCachePort userCachePort;
    private final long maxSizeBytes;
    private final String avatarBaseUrl;

    public AvatarService(UserRepository userRepository, AvatarStoragePort storagePort,
                         UserCachePort userCachePort, long maxSizeBytes, String avatarBaseUrl) {
        this.userRepository = userRepository;
        this.storagePort = storagePort;
        this.userCachePort = userCachePort;
        this.maxSizeBytes = maxSizeBytes;
        this.avatarBaseUrl = avatarBaseUrl;
    }

    @Override
    @Transactional
    public String upload(String username, byte[] bytes, String originalFilename) {
        if (bytes.length > maxSizeBytes) throw new AvatarTooLargeException(maxSizeBytes);

        String extension = detectExtension(bytes);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (user.getAvatarFilename() != null) {
            storagePort.delete(user.getAvatarFilename());
        }

        String filename = storagePort.save(bytes, extension);
        user.setAvatar(filename);
        userRepository.save(user);
        userCachePort.evict(username);

        return storagePort.getPublicUrl(filename)
                .orElse(avatarBaseUrl + "/avatars/" + filename);
    }

    @Override
    @Transactional
    public void delete(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        if (user.getAvatarFilename() == null) return;

        storagePort.delete(user.getAvatarFilename());
        user.clearAvatar();
        userRepository.save(user);
        userCachePort.evict(username);
    }

    // --- Validação por magic bytes ---

    private static final byte[] MAGIC_JPEG  = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] MAGIC_PNG   = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    // WebP: bytes 0-3 = "RIFF", bytes 8-11 = "WEBP"
    private static final byte[] MAGIC_RIFF  = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] MAGIC_WEBP  = {0x57, 0x45, 0x42, 0x50};

    private String detectExtension(byte[] bytes) {
        if (bytes.length >= 3 && startsWith(bytes, MAGIC_JPEG))  return "jpg";
        if (bytes.length >= 8 && startsWith(bytes, MAGIC_PNG))   return "png";
        if (bytes.length >= 12
                && startsWith(bytes, MAGIC_RIFF)
                && startsWith(bytes, 8, MAGIC_WEBP))              return "webp";
        throw new InvalidAvatarFormatException();
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        return startsWith(data, 0, prefix);
    }

    private boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (data.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) return false;
        }
        return true;
    }
}
