-- Tabela exigida pelo ShedLock para coordenação de scheduler em múltiplas instâncias.
-- Garante que o cleanup de refresh tokens execute exatamente uma vez por janela,
-- mesmo com N instâncias da aplicação rodando em paralelo (k8s, ECS, etc).
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
