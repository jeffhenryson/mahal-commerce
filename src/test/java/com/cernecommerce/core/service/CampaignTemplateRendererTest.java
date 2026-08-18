package com.cernecommerce.core.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignTemplateRendererTest {

    private final CampaignTemplateRenderer renderer = new CampaignTemplateRenderer();

    @Test
    void render_replacesSimpleDottedVariable() {
        Map<String, Object> vars = Map.of("cliente", Map.of("nome", "Maria"));

        String result = renderer.render("Ola {{cliente.nome}}!", vars);

        assertThat(result).isEqualTo("Ola Maria!");
    }

    @Test
    void render_allowsInternalWhitespace() {
        Map<String, Object> vars = Map.of("cliente", Map.of("nome", "Maria"));

        String result = renderer.render("Ola {{ cliente.nome }}!", vars);

        assertThat(result).isEqualTo("Ola Maria!");
    }

    @Test
    void render_replacesMultipleOccurrencesOfSameVariable() {
        Map<String, Object> vars = Map.of("cliente", Map.of("nome", "Maria"));

        String result = renderer.render("{{cliente.nome}}, tudo bem, {{cliente.nome}}?", vars);

        assertThat(result).isEqualTo("Maria, tudo bem, Maria?");
    }

    @Test
    void render_keepsUnknownTokenLiteral() {
        Map<String, Object> vars = Map.of("cliente", Map.of("nome", "Maria"));

        String result = renderer.render("Ola {{cliente.sobrenome}}!", vars);

        assertThat(result).isEqualTo("Ola {{cliente.sobrenome}}!");
    }

    @Test
    void render_resolvesLegacySingleBraceAliases() {
        Map<String, Object> vars = Map.of("cliente", Map.of("nome", "Maria", "cashback", "10,00",
                "email", "maria@example.com", "contato", "11999998888"));

        assertThat(renderer.render("Ola {nome}", vars)).isEqualTo("Ola Maria");
        assertThat(renderer.render("Saldo: {saldo}", vars)).isEqualTo("Saldo: 10,00");
        assertThat(renderer.render("Email: {email}", vars)).isEqualTo("Email: maria@example.com");
        assertThat(renderer.render("Tel: {telefone}", vars)).isEqualTo("Tel: 11999998888");
    }

    @Test
    void render_keepsUnknownLegacyAliasLiteral() {
        Map<String, Object> vars = Map.of("cliente", Map.of("nome", "Maria"));

        String result = renderer.render("{sobrenome}", vars);

        assertThat(result).isEqualTo("{sobrenome}");
    }

    @Test
    void render_returnsTemplateUnchangedWhenNoVariables() {
        String result = renderer.render("Mensagem sem variaveis", Map.of());

        assertThat(result).isEqualTo("Mensagem sem variaveis");
    }

    @Test
    void render_returnsNullWhenTemplateIsNull() {
        assertThat(renderer.render(null, Map.of())).isNull();
    }
}
