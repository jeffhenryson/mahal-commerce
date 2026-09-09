package com.cernecommerce.infra.persistence;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registra {@code unaccent(texto)} como função conhecida do HQL (EST-C020).
 *
 * <p>A busca do catálogo já ignorava caixa e não ignorava acento, e o efeito era pior do que
 * "não achar": como há produto cadastrado com e sem acento, cada busca devolvia só o seu grupo —
 * quem digitava "narguile" via 6 de 14 e recebia uma lista plausível e incompleta, sem nenhum
 * sinal de que faltava metade. Aplicar {@code unaccent} nos <b>dois</b> lados da comparação
 * (coluna e termo) faz "Narguilé" achar "narguile" e vice-versa.</p>
 *
 * <p>Registrado pelo SPI {@code META-INF/services/org.hibernate.boot.model.FunctionContributor},
 * e não por uma customização de {@code Dialect}: a função é a mesma em qualquer banco, o que muda
 * é quem a implementa. No Postgres é a extensão {@code unaccent} (migration V122); no H2 do perfil
 * {@code dev} é o alias de {@link H2Unaccent}, criado em {@code db/dev/dev-schema.sql}, para o
 * mesmo HQL rodar nos dois — a mesma paridade que aquele arquivo já mantém para
 * {@code order_number_seq}.</p>
 */
public class UnaccentFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry().registerPattern(
                "unaccent",
                "unaccent(?1)",
                functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry()
                        .resolve(StandardBasicTypes.STRING));
    }
}
