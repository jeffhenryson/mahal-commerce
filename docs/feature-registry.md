# Feature Registry — Mahal Backend

Registro de features/correções implementadas por domínio, atualizado a cada sprint via `/3-implementar` e `/notion-sync`.

---

## auth
*Domínio de segurança, autenticação de usuários, refresh tokens, MFA/TOTP, rate limiting.*

| Feature | Data | Descrição |
|---|---|---|
| base-security | 2026-07-15 | Estrutura básica de segurança com JWT, rotação de refresh token e RBAC. |
| mfa-totp | 2026-07-15 | Autenticação em dois fatores com TOTP e backup codes. |
| oauth-google | 2026-07-15 | Autenticação integrada via Google Sign-In (ID Token validation). |

## notification
*Domínio de envio e preferências de notificações do sistema.*

| Feature | Data | Descrição |
|---|---|---|
| basic-notifications | 2026-07-15 | Motor de envio básico de notificações internas e preferências de e-mail. |

## estoque
*Domínio de gerenciamento de produtos, essências, carvão e controle de inventário.*

| Feature | Data | Descrição |
|---|---|---|
| cadastrar-produto (EST-F001) | 2026-07 | Entidade `Product` (SKU pai) + `ProductVariant`/`ProductAttribute` (grade sabor/tamanho/cor) + `POST`/`GET /estoque/products` (paginado, ID-first sem N+1) + RBAC `ESTOQUE_PRODUCT_READ`/`MANAGE`, migrations V44/V45. Fundação do domínio. |
| controle-saldo-multi-deposito (EST-F002) | 2026-07 | Entidade `Warehouse` (loja física/e-commerce) + `StockBalance` (saldo por SKU/depósito, `@Version` pré-preparado para movimentação) + `POST`/`GET /estoque/warehouses`, `GET /estoque/stock-balance` (zero se nunca movimentado) + RBAC `ESTOQUE_WAREHOUSE_READ`/`MANAGE`, migrations V46/V47. |
| movimentacao-manual (EST-F003) | 2026-07-22 | `StockMovement` (ledger auditável ENTRADA/SAIDA/AJUSTE, `username` sempre do JWT) + `POST /estoque/movements`: `EstoqueService.adjustStock` atualiza `StockBalance` transacionalmente, rejeita SAIDA que deixaria saldo negativo (`InsufficientStockException`, 400) e trata conflito de concorrência via `@Version` (409 `STOCK_UPDATE_CONFLICT`). RBAC `ESTOQUE_STOCK_MANAGE`, migrations V55/V56. `adjustStock` é reutilizado por outros domínios (`compras`, `vendas-balcao`) sem acesso direto a `WarehouseRepository`/`ProductRepository`. |
| alerta-estoque-minimo (EST-F004) | 2026-07-23 | `ReorderPoint` (ponto de reposição por SKU/depósito) + `PUT /estoque/products/{sku}/reorder-point` (cria ou atualiza). `EstoqueService.adjustStock` passa a verificar, após toda movimentação (manual, `compras`, `vendas-balcao`), se o novo saldo ficou abaixo do ponto de reposição e, se sim, notifica via `NotificationUseCase.notify` todos os usuários com `ESTOQUE_STOCK_MANAGE` (`UserRepository.findUsernamesByPermission`, novo método análogo a `findUsernamesByRole`). Sem ponto de reposição cadastrado, nenhuma notificação é disparada. RBAC `ESTOQUE_STOCK_MANAGE` (reaproveitada), migration V61. |
| inventario-contagem (EST-F006) | 2026-07-23 | `StockCount`/`StockCountItem` (contagem cíclica: abertura → registro item a item da quantidade física contada, com snapshot do saldo do sistema no momento → fechamento) + `POST /estoque/counts`, `POST /estoque/counts/{id}/items`, `POST /estoque/counts/{id}/close`. Ao fechar, cada item com divergência (contado != sistema) tem o `StockBalance` ajustado diretamente para a quantidade contada (`StockBalance.reconcile`, não um delta) e gera um `StockMovement` de `AJUSTE` com a magnitude da divergência referenciando a contagem; itens sem divergência não geram movimentação. Reaproveita a notificação de ponto de reposição (`notifyIfBelowReorderPoint`) para os ajustes gerados. `StockCountNotFoundException`/`StockCountNotOpenException` (404/409) impedem registrar itens ou fechar uma contagem inexistente ou já fechada. RBAC `ESTOQUE_COUNT_MANAGE` (nova), migrations V62/V63. |
| paginacao-repositorios | 2026-07-15 | Contrato `PageResult<T>` adicionado aos repositórios stub de Compras, PDV, Ecommerce, Financeiro e Logística (ports out/in, services e controllers), seguindo o padrão já usado por `ProductRepository`. |

## compras
*Domínio de reposição de estoque, pedidos de compra e fornecedores.*

| Feature | Data | Descrição |
|---|---|---|
| recebimento-movimenta-saldo (EST-F009) | 2026-07-23 | Persistência mínima de `Supplier` (tabela/entity/JPA, sem CRUD completo — fornecedor inserido diretamente via `SupplierRepository`, ex.: em testes) + `GoodsReceipt`/`GoodsReceiptItem` (recebimento de mercadoria referenciando `sku`/`warehouseCode`, não IDs numéricos de outro domínio) + `POST /compras/goods-receipts`: `ComprasService.receiveGoods` chama `EstoqueUseCase.adjustStock` com `MovementType.ENTRADA` por item, transacional (reverte se fornecedor ou depósito não existir — `SupplierNotFoundException`/`WarehouseNotFoundException`, 404). `GET /compras/suppliers` deixa de ser stub e passa a listar fornecedores reais. RBAC `COMPRAS_RECEIPT_MANAGE`, migrations V58/V59/V60. |

## financeiro
*Domínio de fluxo de caixa, DRE, conciliação e transações do lounge e e-commerce.*

| Feature | Data | Descrição |
|---|---|---|

## vendas-balcao
*Domínio de Frente de Caixa (PDV), vendas locais da tabacaria.*

| Feature | Data | Descrição |
|---|---|---|
| baixa-automatica-venda (EST-F010) | 2026-07-23 | Venda registrada no PDV dá baixa automática no `StockBalance` do depósito loja: o fluxo de venda chama `EstoqueService.adjustStock` com `MovementType.SAIDA` por item, transacional, reusando o mesmo caminho de `compras` (sem acesso direto a `WarehouseRepository`/`ProductRepository`). Rejeita SAIDA que deixaria saldo negativo (`InsufficientStockException`) e dispara `notifyIfBelowReorderPoint`. Commit `deed2d2`. |

## ecommerce
*Domínio de catálogo online, carrinho de compras e pedidos dos clientes.*

| Feature | Data | Descrição |
|---|---|---|

## logistica
*Domínio de controle de despachos, motoboys e entregas.*

| Feature | Data | Descrição |
|---|---|---|

## gestao-empresarial
*Domínio de controle de filiais, cadastros gerais e parâmetros de funcionamento.*

| Feature | Data | Descrição |
|---|---|---|

## relatorios
*Domínio de consolidação de relatórios e métricas de desempenho.*

| Feature | Data | Descrição |
|---|---|---|

## crm
*Domínio de gestão de relacionamento com clientes, segmentação e campanhas.*

| Feature | Data | Descrição |
|---|---|---|
| cadastro-cliente (F001) | 2026-07-20 | Entidade `Customer` (nome, contato, email único, cpf, origem, cadastradoEm) + criar/buscar cliente + RBAC (`CRM_CUSTOMER_READ`/`CRM_CUSTOMER_MANAGE`) + migration V48. Fundação do módulo CRM. |
| listagem-clientes-rfm (F002) | 2026-07-20 | `GET /crm/customers` paginado com filtro por nome/contato. LTV, cashback, segmento e tags adicionados ao `CustomerResponseDTO` como placeholder (0/"NOVO"/[]) até os domínios de pedidos e cashback existirem. |
| perfil-cliente-360 (F003) | 2026-07-20 | Notas de cliente (`POST`/`GET /crm/customers/{id}/notes`, migration V49) + histórico de pedidos e extrato de cashback (`GET .../orders`, `GET .../cashback`) como endpoints placeholder que sempre retornam `[]` até os domínios de pedidos e cashback existirem. |
| kanban-segmentacao (F004) | 2026-07-20 | Estágio manual do Kanban de atendimento (`CustomerStage`: NOVO_LEAD/EM_ATENDIMENTO/QUALIFICADO/CLIENTE_ATIVO/INATIVO) com `PATCH /crm/customers/{id}/estagio` + trilha de auditoria (`GET .../estagio/historico`, migration V50). Distinto do segmento RFM placeholder. |
| dashboard-overview (F005) | 2026-07-21 | `GET /crm/dashboard/overview` — total de clientes, ativos (`estagio != INATIVO`) e contagem por estágio como dados reais; LTV médio, disparos WhatsApp/mês e contagem por segmento RFM como placeholder. Sem migration nova. |
| tags-segmentos (F007) | 2026-07-21 | CRUD de tags (`POST`/`GET`/`DELETE /crm/tags`) + associação cliente↔tag (`POST`/`DELETE /crm/customers/{id}/tags`, `GET .../tags`), migration V51. Campo `tags` de `CustomerResponseDTO` (antes placeholder) passa a ser real em `POST`/`GET /crm/customers/{id}` e `PATCH .../estagio`; continua `[]` na listagem paginada (evita N+1). |
| exportacao-csv-clientes (F009) | 2026-07-21 | `GET /crm/customers/export` — exportação server-side em CSV (RFC 4180, BOM UTF-8) de toda a base filtrada por `search`, sem paginação. Colunas: id, nome, contato, email, cpf, origem, cadastradoEm, estagio. Sem migration nova. |
| automacoes-campanhas (F006) | 2026-07-21 | CRUD de automações de campanha (`POST`/`GET`/`PATCH .../ativa`/`DELETE /crm/automacoes`, migration V52) + disparo manual (`POST .../disparar`) que cria log por cliente do `segmentoAlvo` (`CustomerStage`, não RFM) com status `PENDENTE_INTEGRACAO` — sem envio real (depende de integracao-canal-envio) e `convertidoEm` sempre `null` (depende de pedidos). |
| integracao-canal-envio (F008) | 2026-07-21 | `GET /crm/canais/status` — status real de conexão dos canais de envio (WhatsApp/E-mail), substitui o badge fixo hardcoded no frontend. `EmailPort` ganhou `channelStatus()`, implementado por `LoggingEmailAdapter`/`MailpitEmailAdapter`/`ResendEmailAdapter` (reflete o adapter ativo no profile). WhatsApp sempre reporta desconectado — sem integração real. Sem migration. Última feature do backlog original de CRM (F001-F009). |

## security
*Correções de segurança e infra sensível.*

| Correção | Data | Descrição |
|---|---|---|
| remover-segredos-hardcoded-docker-compose (C001) | 2026-07-21 | Segredos reais movidos de `docker-compose.yml` para `.env` (gitignored), referenciados via `${VAR}`. Sem rotação nesta etapa (decisão do usuário) — ver nota em `docs/backlog.md`. |
| totp-key-sem-default-hardcoded-prod (C002) | 2026-07-21 | Removido o fallback hardcoded de `TOTP_ENCRYPTION_KEY` em `docker-compose.prod.yml`; `ProdStartupValidator` reforçado para rejeitar explicitamente o valor default conhecido. Sem rotação nesta etapa — ver nota em `docs/backlog.md`. |
| validar-seed-dev-password-hml-prod (C005) | 2026-07-22 | `ProdStartupValidator`/`HmlStartupValidator` passam a rejeitar boot quando `DEV_EMAIL` está definido mas `DEV_PASSWORD` está ausente ou usa o default `Dev@secure1!` do repositório — evita que `DevRoleBootstrapConfig` crie uma conta `ROLE_DEV` real com senha pública hardcoded. |

## rbac
*Correções de controle de acesso baseado em roles/permissões.*

| Correção | Data | Descrição |
|---|---|---|
| evictar-cache-authorities-ao-alterar-permissoes (C003) | 2026-07-22 | `RoleService.assignPermission`/`removePermission` evictam o cache `userDetails` de todo usuário com a role alterada (`UserRepository.findUsernamesByRole` + `UserCachePort.evict`), não só de quem disparou a ação. |
| preauthorize-controllers-stub (C004) | 2026-07-22 | `@PreAuthorize` adicionado aos 5 endpoints stub (`Compras`/`Ecommerce`/`Financeiro`/`Logistica`/`Pdv`), removendo o fallback `anyRequest().authenticated()`. Novas permissões `COMPRAS_READ`/`ECOMMERCE_READ`/`FINANCEIRO_READ`/`LOGISTICA_READ`/`PDV_READ`, migration V53. |
| alinhar-permissoes-seed-dev (C006) | 2026-07-22 | `SeedConfig.ADMIN_PERMISSIONS` (seed dev, `ROLE_ADMIN`) ganhou `ESTOQUE_PRODUCT_READ`/`MANAGE` e `ESTOQUE_WAREHOUSE_READ`/`MANAGE`, alinhando com `DevRoleBootstrapConfig` — o usuário `admin` de teste local deixa de receber 403 inesperado em `/estoque/**`. |
| it-rbac-ponta-a-ponta (C007) | 2026-07-22 | `RbacEndToEndIT` — primeiro teste que exercita RBAC pelo pipeline real (criar usuário → atribuir role → assignPermission → login real → JWT → endpoint protegido), sem nenhuma authority injetada via mock. Reaproveita `PDV_READ`/`GET /pdv/sessions` (C004) como alvo, sem endpoint/permissão novos. |
| concorrencia-tokens-cas (C008) | 2026-07-22 | 4 novos ITs de concorrência real (5 threads, `CountDownLatch`, mesmo padrão de `VerifyEmailConcurrencyIT`): `TotpBackupCodeConcurrencyIT`, `PasswordResetConcurrencyIT`, `TotpChallengeConcurrencyIT`, `DevChallengeConcurrencyIT` — todos confirmam exatamente 1 sucesso por CAS, nunca duplo sucesso. Descoberto o bug de validação C022 durante a escrita do teste de backup code. |
| teste-revoke-refresh-token-idor (C009) | 2026-07-22 | `RefreshTokenRepositoryImplIT` — primeiro teste dedicado a uma classe `*RepositoryImpl` do projeto: `revokeByIdForUser` contra o banco real prova o isolamento IDOR (usuário A não revoga sessão de B), sucesso no próprio dono, e id inexistente lança `SessionNotFoundException`. |
| totpcode-size-rejeita-backup-code-real (C022) | 2026-07-22 | `ChangePasswordRequest.totpCode` — `@Size(max=8)` corrigido para `max=14`, aceitando o formato real dos backup codes (`XXXX-XXXX-XXXX`). Antes, `PUT /users/me/password` rejeitava todo backup code real por validação, tornando a troca de senha com backup code impossível na prática. Regressão coberta em `UserProfileAndSessionsTest`. |

## infra / cicd / persistence / performance / docs
*Correções de infraestrutura, pipeline, persistência, performance e documentação.*

| Correção | Data | Descrição |
|---|---|---|
| indices-username-email-password-reset (C010) | 2026-07-22 | Migration V54 — índices em `email_verification_codes(username)` e `password_reset_tokens(username)`, eliminando full table scan em `findFirstByUsernameOrderBy...`/`deleteByUsername`. |
| batching-rollback-v26 (C011) | 2026-07-22 | Documentação de processo em `docs/persistence.md` (V26 já aplicada, não editável): padrão de batching por lotes de id para UPDATEs em massa futuros, plano de rollback de V26 (impossível sem backup pré-migration), e checklist para novas migrations de UPDATE/DELETE em massa. Sem mudança de código. |
| dimensionar-pool-hikaricp (C012) | 2026-07-22 | `server.tomcat.threads.max=${TOMCAT_MAX_THREADS:50}` definido explicitamente em hml/prod (era o default implícito de 200). `HIKARI_MAX_POOL_SIZE` (10) não alterado — depende do vCPU count real da instância de banco em produção. Documentado em `docs/persistence.md`. |
| documentar-frontends-docker-compose (C013) | 2026-07-22 | Comentário no topo de `docker-compose.yml` + nova seção "Topologia de deploy" em `docs/architecture.md`: os frontends vivem em repositórios sibling, cada um com seu próprio `docker-compose.yml`. Descoberto um terceiro `docker-compose.yml` (repo separado, um nível acima) que é o que efetivamente roda o ambiente local — ver nota em `docs/backlog.md`. |
| healthchecks-prometheus-grafana (C014) | 2026-07-22 | Healthchecks adicionados a `prometheus-mahal` (`/-/healthy`) e `grafana-mahal` (`/api/health`) em `docker-compose.yml`. Validado via `docker compose config`; não validado contra containers reais (ver nota em `docs/backlog.md`). |
| eliminar-build-docker-duplicado (C015) | 2026-07-22 | `.github/workflows/ci.yml` — `build-test` builda a imagem uma única vez e sobe como artifact; `deploy-ecr` baixa, `docker load` e só `tag`+`push` para o ECR, sem rebuild. Garante paridade byte-a-byte entre imagem testada e publicada. |
| validar-avatar-base-url-hml (C016) | 2026-07-22 | `HmlStartupValidator` ganhou validação de `avatar.base-url` (rejeita ausente/`localhost`/`example.com`); `ProdStartupValidator` reforçado para também rejeitar `example.com`. Fecha a Sprint 2. |
| rebranding-cerne-para-mahal (C017) | 2026-07-22 | Renomeação `cerne-commerce` → `mahal-commerce` em 14 arquivos. Pacote Java `com.cernecommerce` mantido intencionalmente (fora de escopo). Build Maven + `docker build` validados de ponta a ponta. Ver nota em `docs/backlog.md`. |
| migrations-seed-sem-on-conflict (C018) | 2026-07-22 | Documentação de processo em `docs/persistence.md` (V19/V45/V47 já aplicadas, não editáveis sem `flyway repair`): risco de INSERT falhar em rebaseline, + checklist para toda migration de seed futura usar `ON CONFLICT DO NOTHING`. Sem mudança de código. |
| scheduler-pool-size (C019) | 2026-07-22 | `spring.task.scheduling.pool.size=${SCHEDULER_POOL_SIZE:4}` em `application.properties` — evita que os jobs `@Scheduled` concorrentes às 03:45/03:30 serializem no único thread default. `SchedulerPoolSizeTest` confirma o pool real. |
| nome-servico-docker-compose (C020) | 2026-07-22 | Chave do serviço em `docker-compose.yml` renomeada de `app` para `mahal-backend`, alinhando com o padrão `-mahal`. Comentário do topo corrigido para o alvo real do Prometheus. Sem mudança de código. |
| documentacao-desatualizada (C021) | 2026-07-22 | `docs/testing.md`/`README.md` — contagem de arquivos de teste corrigida para 128. `docs/security.md` — descrição do H2 console corrigida: `permitAll()` total (mitigado só por `@Profile("dev")`). |
