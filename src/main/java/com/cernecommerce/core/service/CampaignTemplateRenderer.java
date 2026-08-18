package com.cernecommerce.core.service;

import com.cernecommerce.core.ports.in.CampaignTemplateRendererUseCase;

import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renderiza variáveis {@code {{cliente.nome}}} (e o alias legado de chave simples, ex.
 * {@code {nome}}) em templates de automação do CRM — replica byte-a-byte a lógica de
 * {@code automacao-variaveis.ts} do mahal-admin, para que o disparo real do servidor produza a
 * mesma mensagem que o navegador produzia antes (ver crm/webhook-disparo-real, F008).
 *
 * <p>Token desconhecido permanece literal na saída (não é removido) — comportamento deliberado
 * para o operador perceber o erro de digitação em vez de perder a variável silenciosamente.</p>
 */
public class CampaignTemplateRenderer implements CampaignTemplateRendererUseCase {

    private static final Pattern DOUBLE_BRACE = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*\\}\\}");
    private static final Pattern SINGLE_BRACE = Pattern.compile("\\{\\s*(\\w+)\\s*\\}");

    private static final Map<String, String> ALIASES_LEGADOS = Map.of(
            "nome", "cliente.nome",
            "saldo", "cliente.cashback",
            "email", "cliente.email",
            "telefone", "cliente.contato"
    );

    @Override
    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        String comChaveDupla = replace(DOUBLE_BRACE, template, chave -> resolve(variables, chave));
        return replace(SINGLE_BRACE, comChaveDupla, chave -> {
            String alias = ALIASES_LEGADOS.get(chave);
            return alias == null ? null : resolve(variables, alias);
        });
    }

    private String replace(Pattern pattern, String input, Function<String, String> resolver) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String resolved = resolver.apply(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(resolved != null ? resolved : matcher.group(0)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String resolve(Map<String, Object> variables, String dottedKey) {
        Object current = variables;
        for (String part : dottedKey.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current == null ? null : String.valueOf(current);
    }
}
