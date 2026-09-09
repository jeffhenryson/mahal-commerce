package com.cernecommerce.core.ports.out.sse;

import java.util.Optional;

/**
 * Bilhetes de uso único para abrir o stream SSE de notificações (PLAT-C051).
 *
 * <p>Existe porque a API {@code EventSource} do navegador — a única forma padrão de consumir SSE
 * no cliente — <b>não envia headers</b>, e portanto não tem como mandar o
 * {@code Authorization: Bearer} que autentica todo o resto da API. Sem um caminho alternativo,
 * {@code GET /notifications/stream} respondia 401 para todo mundo e a notificação em tempo real
 * simplesmente não existia.</p>
 *
 * <p>A alternativa óbvia — aceitar o JWT em {@code ?token=} — foi recusada: query string entra em
 * log de acesso, em histórico de proxy e no cabeçalho {@code Referer}, e o access token vale 15
 * minutos em toda a API. O bilhete é opaco, vive segundos, só serve para uma rota e é queimado no
 * primeiro uso.</p>
 */
public interface SseTicketPort {

    /** Emite um bilhete novo para {@code username}, válido por {@code ttlSeconds}. */
    String issue(String username, long ttlSeconds);

    /**
     * Resolve e <b>consome</b> o bilhete. Devolve vazio se ele não existir, já tiver sido usado ou
     * tiver expirado — os três casos são indistinguíveis de propósito para quem chama.
     */
    Optional<String> consume(String ticket);
}
