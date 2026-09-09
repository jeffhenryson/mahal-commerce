package com.cernecommerce.infra.persistence;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Implementação de {@code unaccent} para o H2 do perfil {@code dev} (EST-C020) — em
 * {@code hml}/{@code prod} quem responde por essa função é a extensão homônima do Postgres,
 * criada na migration V122.
 *
 * <p>Existe para o mesmo HQL valer nos dois bancos: sem ela, a query de busca do catálogo
 * quebraria em toda execução de teste, e a alternativa — não usar a função e deixar a busca
 * sensível a acento em dev — faria o ambiente de desenvolvimento discordar do de produção
 * justamente no comportamento que esta correção introduziu.</p>
 *
 * <p>Decompõe em NFD e descarta as marcas de combinação, que é como o {@code unaccent} do
 * Postgres se comporta para o alfabeto latino. O alias é criado em
 * {@code src/main/resources/db/dev/dev-schema.sql}.</p>
 */
public final class H2Unaccent {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private H2Unaccent() {
    }

    /** Chamada pelo H2 via {@code CREATE ALIAS}; precisa ser pública e estática. */
    public static String unaccent(String value) {
        if (value == null) {
            return null;
        }
        return COMBINING_MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
    }
}
