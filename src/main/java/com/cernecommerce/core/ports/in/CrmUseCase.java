package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.ChannelStatus;
import com.cernecommerce.core.domain.model.crm.CrmDashboardOverview;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.StageTransition;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Port de entrada do domínio <b>crm</b>.
 */
public interface CrmUseCase {

    /**
     * Cria um cliente. {@code contato} e {@code email} são opcionais desde CRM-C005 — só
     * {@code nome} e ao menos um identificador (cpf, email ou contato) são exigidos, checado no
     * compact constructor de {@link Customer}.
     *
     * @throws com.cernecommerce.core.domain.exception.crm.DuplicateCustomerEmailException
     *         se o email já estiver cadastrado
     * @throws com.cernecommerce.core.domain.exception.crm.DuplicateCustomerCpfException
     *         se o cpf já estiver cadastrado
     */
    Customer createCustomer(String nome, String contato, String email, String cpf, String origem);

    /**
     * Busca um cliente por id. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se não existir.
     */
    Customer findCustomerById(Long id);

    /**
     * Resolve nome de clientes em lote, para enriquecer telas de outro domínio (ex.: pedidos) sem
     * uma consulta por linha. Id sem cliente correspondente simplesmente não aparece no mapa — ao
     * contrário de {@link #findCustomerById}, não é um lookup pontual e não lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}.
     */
    Map<Long, String> findCustomerNames(Collection<Long> customerIds);

    /**
     * Busca pontual por CPF, email ou contato — o "CPF na nota?" do balcão (CRM-F002). Informe
     * exatamente um critério; os demais como {@code null}. Tentados nessa ordem de prioridade
     * quando mais de um vier preenchido: cpf (identificador oficial) → email → contato.
     *
     * @throws IllegalArgumentException se nenhum critério for informado
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException
     *         se não achar ninguém pelo critério informado
     */
    Customer lookupCustomer(String cpf, String email, String contato);

    /**
     * Lista clientes paginados, filtrando por nome ou contato quando {@code search} não for
     * nulo/vazio.
     */
    PageResult<Customer> listCustomers(String search, int page, int size);

    /**
     * Lista todos os clientes correspondentes ao filtro, sem paginação — usado para exportação
     * (ex.: CSV).
     */
    List<Customer> listCustomersForExport(String search);

    /**
     * Adiciona uma nota a um cliente. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir.
     */
    CustomerNote addNote(Long customerId, String autor, String texto);

    /**
     * Lista as notas de um cliente, mais recentes primeiro. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir.
     */
    List<CustomerNote> listNotes(Long customerId);

    /**
     * Move um cliente para um novo estágio no Kanban de atendimento, registrando a transição.
     * Lança {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir, ou {@link IllegalArgumentException} se {@code novoEstagio} for
     * igual ao estágio atual.
     */
    Customer moveStage(Long customerId, CustomerStage novoEstagio, String autor);

    /**
     * Lista a trilha de transições de estágio de um cliente, mais recentes primeiro. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir.
     */
    List<StageTransition> listStageHistory(Long customerId);

    /** Agrega as métricas do CRM para o dashboard overview. */
    CrmDashboardOverview getDashboardOverview();

    /**
     * Cria uma tag. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException}
     * se o nome já existir.
     */
    Tag createTag(String nome);

    /** Lista todas as tags com a contagem de clientes associados a cada uma. */
    List<TagSummary> listTags();

    /**
     * Remove uma tag e todas as suas associações. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.TagNotFoundException}
     * se não existir.
     */
    void deleteTag(Long tagId);

    /**
     * Associa uma tag a um cliente. Idempotente. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir, ou
     * {@link com.cernecommerce.core.domain.exception.crm.TagNotFoundException}
     * se a tag não existir.
     */
    void addTagToCustomer(Long customerId, Long tagId);

    /**
     * Remove a associação entre um cliente e uma tag. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir, ou
     * {@link com.cernecommerce.core.domain.exception.crm.TagNotFoundException}
     * se a tag não existir.
     */
    void removeTagFromCustomer(Long customerId, Long tagId);

    /**
     * Lista as tags associadas a um cliente. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException}
     * se o cliente não existir.
     */
    List<Tag> listCustomerTags(Long customerId);

    /** Cria uma automação de campanha (ativa por padrão). */
    CampaignAutomation createAutomation(String nome, CampaignTrigger gatilho, CustomerStage segmentoAlvo,
            CampaignChannel canal, String template, String webhookUrl, Map<String, String> webhookHeaders);

    /** Lista todas as automações de campanha. */
    List<CampaignAutomation> listAutomations();

    /**
     * Ativa ou desativa uma automação. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se não existir.
     */
    CampaignAutomation setAutomationActive(Long automationId, boolean ativa);

    /**
     * Atualiza todos os campos editáveis de uma automação existente, sem precisar recriá-la.
     * Lança {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se não existir.
     */
    CampaignAutomation updateAutomation(Long automationId, String nome, CampaignTrigger gatilho,
            CustomerStage segmentoAlvo, CampaignChannel canal, String template, String webhookUrl,
            Map<String, String> webhookHeaders);

    /**
     * Remove uma automação e seu log de disparos. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se não existir.
     */
    void deleteAutomation(Long automationId);

    /**
     * Dispara uma automação manualmente: resolve os clientes do {@code segmentoAlvo} e cria uma
     * {@link CampaignLogEntry} por cliente. Quando a automação tem {@code webhookUrl} configurado,
     * envia de verdade um POST para essa URL (status {@code ENVIADO}/{@code FALHA} conforme o
     * resultado); sem {@code webhookUrl}, mantém o comportamento legado — status
     * {@code PENDENTE_INTEGRACAO}, sem envio real. Um cliente-alvo com webhook indisponível não
     * interrompe o disparo para os demais. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se a automação não existir.
     */
    List<CampaignLogEntry> dispatchAutomation(Long automationId);

    /**
     * Dispara um payload de teste para o webhook configurado, com um cliente fictício — não
     * persiste {@link CampaignLogEntry}. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se a automação não existir, ou
     * {@link com.cernecommerce.core.domain.exception.crm.AutomationWebhookNotConfiguredException}
     * se não tiver {@code webhookUrl} configurado.
     */
    WebhookTestResult testAutomation(Long automationId);

    /**
     * Lista o log de disparos de uma automação, mais recentes primeiro. Lança
     * {@link com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException}
     * se a automação não existir.
     */
    List<CampaignLogEntry> listAutomationLog(Long automationId);

    /**
     * Status de conexão dos canais de envio (WhatsApp/E-mail) — substitui o badge fixo
     * "API WhatsApp: Conectada" hoje hardcoded no frontend. WhatsApp não possui integração
     * real ainda, por isso sempre reporta desconectado.
     */
    List<ChannelStatus> getChannelStatus();
}
