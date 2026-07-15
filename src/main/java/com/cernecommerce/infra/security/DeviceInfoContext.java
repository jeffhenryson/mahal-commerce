package com.cernecommerce.infra.security;

/**
 * Thread-local que carrega IP e User-Agent da requisição corrente.
 * Populado pelo TraceIdFilter antes de qualquer processamento de negócio;
 * limpo ao final de cada request para evitar vazamento entre requisições.
 */
public final class DeviceInfoContext {

    private static final ThreadLocal<DeviceInfo> HOLDER = new ThreadLocal<>();

    private DeviceInfoContext() {}

    public static void set(String ipAddress, String userAgent) {
        HOLDER.set(new DeviceInfo(ipAddress, userAgent));
    }

    public static DeviceInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record DeviceInfo(String ipAddress, String userAgent) {}
}
