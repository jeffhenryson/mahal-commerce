package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.AutomationWebhookNotConfiguredException;
import com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.crm.DuplicateCustomerCpfException;
import com.cernecommerce.core.domain.exception.crm.DuplicateCustomerEmailException;
import com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException;
import com.cernecommerce.core.domain.exception.crm.TagNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.ChannelStatus;
import com.cernecommerce.core.domain.model.crm.ChannelType;
import com.cernecommerce.core.domain.model.crm.CrmDashboardOverview;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.StageTransition;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;
import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.CampaignTemplateRendererUseCase;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CampaignWebhookPort;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.crm.StageTransitionRepository;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class CrmService implements CrmUseCase {

    // Placeholder até os domínios de pedidos/cashback e de campanhas existirem —
    // ver crm/listagem-clientes-rfm e crm/automacoes-campanhas.
    private static final BigDecimal LTV_MEDIO_PLACEHOLDER = BigDecimal.ZERO;
    private static final long DISPAROS_WHATSAPP_PLACEHOLDER = 0L;
    private static final String SEGMENTO_PLACEHOLDER = "NOVO";

    // Payload do webhook de automações — identifica a origem para os workflows externos
    // (n8n/Make) já configurados pelo cliente, mesmo literal usado quando o navegador do
    // operador disparava o POST diretamente (ver crm/webhook-disparo-real, F008).
    private static final String WEBHOOK_ORIGEM = "mahal-admin";
    private static final String LOJA_NOME = "Mahal Tabacaria";
    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_PT_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_PT_BR = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<CustomerStage, String> ESTAGIO_LABEL = Map.of(
            CustomerStage.NOVO_LEAD, "Novo Lead",
            CustomerStage.EM_ATENDIMENTO, "Em Atendimento",
            CustomerStage.QUALIFICADO, "Qualificado",
            CustomerStage.CLIENTE_ATIVO, "Cliente Ativo",
            CustomerStage.INATIVO, "Inativo");

    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final StageTransitionRepository stageTransitionRepository;
    private final TagRepository tagRepository;
    private final CustomerTagRepository customerTagRepository;
    private final CampaignAutomationRepository campaignAutomationRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final EmailPort emailPort;
    private final CashbackUseCase cashbackUseCase;
    private final CampaignWebhookPort campaignWebhookPort;
    private final CampaignTemplateRendererUseCase templateRenderer;

    public CrmService(CustomerRepository customerRepository, CustomerNoteRepository customerNoteRepository,
            StageTransitionRepository stageTransitionRepository, TagRepository tagRepository,
            CustomerTagRepository customerTagRepository, CampaignAutomationRepository campaignAutomationRepository,
            CampaignLogRepository campaignLogRepository, EmailPort emailPort, CashbackUseCase cashbackUseCase,
            CampaignWebhookPort campaignWebhookPort, CampaignTemplateRendererUseCase templateRenderer) {
        this.customerRepository = customerRepository;
        this.customerNoteRepository = customerNoteRepository;
        this.stageTransitionRepository = stageTransitionRepository;
        this.tagRepository = tagRepository;
        this.customerTagRepository = customerTagRepository;
        this.campaignAutomationRepository = campaignAutomationRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.emailPort = emailPort;
        this.cashbackUseCase = cashbackUseCase;
        this.campaignWebhookPort = campaignWebhookPort;
        this.templateRenderer = templateRenderer;
    }

    @Override
    @Transactional
    public Customer createCustomer(String nome, String contato, String email, String cpf, String origem) {
        // CRM-C005: email e cpf são opcionais agora — só checa duplicidade do que veio preenchido.
        if (email != null && !email.isBlank()) {
            customerRepository.findByEmail(email).ifPresent(c -> {
                throw new DuplicateCustomerEmailException(email);
            });
        }
        if (cpf != null && !cpf.isBlank()) {
            customerRepository.findByCpf(cpf).ifPresent(c -> {
                throw new DuplicateCustomerCpfException(cpf);
            });
        }
        Customer customer = Customer.create(nome, contato, email, cpf, origem);
        return customerRepository.save(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public Customer findCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findCustomerNames(Collection<Long> customerIds) {
        return customerRepository.findByIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::id, Customer::nome));
    }

    @Override
    @Transactional(readOnly = true)
    public Customer lookupCustomer(String cpf, String email, String contato) {
        // Ordem de prioridade quando mais de um critério vier preenchido: cpf (oficial) → email →
        // contato — o mais forte primeiro, para não devolver o cliente errado por coincidência de
        // telefone quando o CPF, mais específico, também foi informado.
        if (cpf != null && !cpf.isBlank()) {
            return customerRepository.findByCpf(cpf)
                    .orElseThrow(() -> new CustomerNotFoundException("cpf " + cpf));
        }
        if (email != null && !email.isBlank()) {
            return customerRepository.findByEmail(email)
                    .orElseThrow(() -> new CustomerNotFoundException("email " + email));
        }
        if (contato != null && !contato.isBlank()) {
            return customerRepository.findByContato(contato)
                    .orElseThrow(() -> new CustomerNotFoundException("contato " + contato));
        }
        throw new IllegalArgumentException("informe cpf, email ou contato para a busca");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Customer> listCustomers(String search, int page, int size) {
        return customerRepository.findAll(search, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> listCustomersForExport(String search) {
        return customerRepository.findAllForExport(search);
    }

    @Override
    @Transactional
    public CustomerNote addNote(Long customerId, String autor, String texto) {
        requireCustomer(customerId);
        return customerNoteRepository.save(CustomerNote.create(customerId, autor, texto));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerNote> listNotes(Long customerId) {
        requireCustomer(customerId);
        return customerNoteRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional
    public Customer moveStage(Long customerId, CustomerStage novoEstagio, String autor) {
        Customer customer = requireCustomer(customerId);
        stageTransitionRepository.save(StageTransition.create(customerId, customer.estagio(), novoEstagio, autor));
        return customerRepository.save(customer.withEstagio(novoEstagio));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StageTransition> listStageHistory(Long customerId) {
        requireCustomer(customerId);
        return stageTransitionRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public CrmDashboardOverview getDashboardOverview() {
        long total = customerRepository.countAll();
        return new CrmDashboardOverview(
                total,
                customerRepository.countActive(),
                LTV_MEDIO_PLACEHOLDER,
                DISPAROS_WHATSAPP_PLACEHOLDER,
                Map.of(SEGMENTO_PLACEHOLDER, total),
                customerRepository.countByStage());
    }

    @Override
    @Transactional
    public Tag createTag(String nome) {
        tagRepository.findByNome(nome).ifPresent(t -> {
            throw new DuplicateTagNameException(nome);
        });
        return tagRepository.save(Tag.create(nome));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagSummary> listTags() {
        return tagRepository.findAllWithCustomerCount();
    }

    @Override
    @Transactional
    public void deleteTag(Long tagId) {
        requireTag(tagId);
        tagRepository.deleteById(tagId);
    }

    @Override
    @Transactional
    public void addTagToCustomer(Long customerId, Long tagId) {
        requireCustomer(customerId);
        requireTag(tagId);
        customerTagRepository.associate(customerId, tagId);
    }

    @Override
    @Transactional
    public void removeTagFromCustomer(Long customerId, Long tagId) {
        requireCustomer(customerId);
        requireTag(tagId);
        customerTagRepository.disassociate(customerId, tagId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tag> listCustomerTags(Long customerId) {
        requireCustomer(customerId);
        return customerTagRepository.findTagsByCustomerId(customerId);
    }

    @Override
    @Transactional
    public CampaignAutomation createAutomation(String nome, CampaignTrigger gatilho, CustomerStage segmentoAlvo,
            CampaignChannel canal, String template, String webhookUrl, Map<String, String> webhookHeaders) {
        CampaignAutomation created = CampaignAutomation.of(null, nome, gatilho, segmentoAlvo, canal, template, true,
                Instant.now(), webhookUrl, webhookHeaders);
        return campaignAutomationRepository.save(created);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignAutomation> listAutomations() {
        return campaignAutomationRepository.findAll();
    }

    @Override
    @Transactional
    public CampaignAutomation setAutomationActive(Long automationId, boolean ativa) {
        CampaignAutomation automation = requireAutomation(automationId);
        return campaignAutomationRepository.save(automation.withAtiva(ativa));
    }

    @Override
    @Transactional
    public CampaignAutomation updateAutomation(Long automationId, String nome, CampaignTrigger gatilho,
            CustomerStage segmentoAlvo, CampaignChannel canal, String template, String webhookUrl,
            Map<String, String> webhookHeaders) {
        CampaignAutomation automation = requireAutomation(automationId);
        return campaignAutomationRepository.save(
                automation.withDetails(nome, gatilho, segmentoAlvo, canal, template, webhookUrl, webhookHeaders));
    }

    @Override
    @Transactional
    public void deleteAutomation(Long automationId) {
        requireAutomation(automationId);
        campaignAutomationRepository.deleteById(automationId);
    }

    @Override
    @Transactional
    public List<CampaignLogEntry> dispatchAutomation(Long automationId) {
        CampaignAutomation automation = requireAutomation(automationId);
        List<Customer> targets = customerRepository.findByEstagio(automation.segmentoAlvo());
        return targets.stream()
                .map(customer -> campaignLogRepository.save(dispatchToCustomer(automation, customer)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignLogEntry> listAutomationLog(Long automationId) {
        requireAutomation(automationId);
        return campaignLogRepository.findByAutomationId(automationId);
    }

    @Override
    @Transactional(readOnly = true)
    public WebhookTestResult testAutomation(Long automationId) {
        CampaignAutomation automation = requireAutomation(automationId);
        if (!automation.hasWebhook()) {
            throw new AutomationWebhookNotConfiguredException(automationId);
        }
        String mensagem = templateRenderer.render(automation.template(), buildTestVariables(automation));
        Map<String, Object> payload = buildTestPayload(automation, mensagem);
        WebhookDispatchResult result = campaignWebhookPort.send(automation.webhookUrl(), automation.webhookHeaders(),
                payload);
        return new WebhookTestResult(result.success(), result.statusCode(), result.errorMessage(), payload);
    }

    /** Sem webhook configurado, mantém o comportamento legado — só registra o log, sem envio real. */
    private CampaignLogEntry dispatchToCustomer(CampaignAutomation automation, Customer customer) {
        if (!automation.hasWebhook()) {
            return CampaignLogEntry.create(automation.id(), customer.id());
        }
        String mensagem = templateRenderer.render(automation.template(), buildTemplateVariables(automation, customer));
        Map<String, Object> payload = buildWebhookPayload(automation, customer, mensagem, false);
        WebhookDispatchResult result = campaignWebhookPort.send(automation.webhookUrl(), automation.webhookHeaders(),
                payload);
        return result.success()
                ? CampaignLogEntry.enviado(automation.id(), customer.id())
                : CampaignLogEntry.falha(automation.id(), customer.id(), result.errorMessage());
    }

    private Map<String, Object> buildTemplateVariables(CampaignAutomation automation, Customer customer) {
        BigDecimal cashback = cashbackUseCase.getCustomerBalance(customer.id()).available();
        List<String> tagNomes = customerTagRepository.findTagsByCustomerId(customer.id()).stream()
                .map(Tag::nome).toList();

        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("nome", customer.nome());
        cliente.put("primeiroNome", primeiroNome(customer.nome()));
        cliente.put("contato", customer.contato());
        cliente.put("whatsapp", toWhatsAppNumber(customer.contato()));
        cliente.put("email", customer.email());
        cliente.put("cpf", customer.cpf());
        cliente.put("origem", customer.origem());
        cliente.put("segmento", SEGMENTO_PLACEHOLDER);
        cliente.put("estagio", ESTAGIO_LABEL.getOrDefault(customer.estagio(), customer.estagio().name()));
        cliente.put("cadastradoEm", formatDataPtBr(customer.cadastradoEm()));
        cliente.put("ltv", formatMoedaPtBr(LTV_MEDIO_PLACEHOLDER));
        cliente.put("cashback", formatMoedaPtBr(cashback));
        cliente.put("tags", String.join(", ", tagNomes));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("cliente", cliente);
        variables.put("loja", Map.of("nome", LOJA_NOME));
        variables.put("data", dataVariables());
        variables.put("automacao", Map.of("nome", automation.nome(), "id", automation.id()));
        return variables;
    }

    private Map<String, Object> buildWebhookPayload(CampaignAutomation automation, Customer customer,
            String mensagem, boolean teste) {
        BigDecimal cashback = cashbackUseCase.getCustomerBalance(customer.id()).available();
        List<String> tagNomes = customerTagRepository.findTagsByCustomerId(customer.id()).stream()
                .map(Tag::nome).toList();

        Map<String, Object> clientePayload = new LinkedHashMap<>();
        clientePayload.put("id", customer.id());
        clientePayload.put("nome", customer.nome());
        clientePayload.put("contato", customer.contato());
        clientePayload.put("whatsapp", toWhatsAppNumber(customer.contato()));
        clientePayload.put("email", customer.email());
        clientePayload.put("cpf", customer.cpf());
        clientePayload.put("segmento", SEGMENTO_PLACEHOLDER);
        clientePayload.put("estagio", customer.estagio().name());
        clientePayload.put("ltv", LTV_MEDIO_PLACEHOLDER);
        clientePayload.put("cashback", cashback);
        clientePayload.put("tags", tagNomes);

        return buildPayload(automation, clientePayload, mensagem, teste);
    }

    private Map<String, Object> buildTestVariables(CampaignAutomation automation) {
        Map<String, Object> cliente = new LinkedHashMap<>();
        cliente.put("nome", "Cliente de Teste");
        cliente.put("primeiroNome", "Cliente");
        cliente.put("contato", "5511999999999");
        cliente.put("whatsapp", "5511999999999");
        cliente.put("email", "teste@mahal.dev");
        cliente.put("cpf", "000.000.000-00");
        cliente.put("origem", "teste");
        cliente.put("segmento", SEGMENTO_PLACEHOLDER);
        cliente.put("estagio", ESTAGIO_LABEL.get(CustomerStage.NOVO_LEAD));
        cliente.put("cadastradoEm", formatDataPtBr(Instant.now()));
        cliente.put("ltv", formatMoedaPtBr(BigDecimal.ZERO));
        cliente.put("cashback", formatMoedaPtBr(BigDecimal.ZERO));
        cliente.put("tags", "");

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("cliente", cliente);
        variables.put("loja", Map.of("nome", LOJA_NOME));
        variables.put("data", dataVariables());
        variables.put("automacao", Map.of("nome", automation.nome(), "id", automation.id()));
        return variables;
    }

    private Map<String, Object> buildTestPayload(CampaignAutomation automation, String mensagem) {
        Map<String, Object> clientePayload = new LinkedHashMap<>();
        clientePayload.put("id", 0L);
        clientePayload.put("nome", "Cliente de Teste");
        clientePayload.put("contato", "5511999999999");
        clientePayload.put("whatsapp", "5511999999999");
        clientePayload.put("email", "teste@mahal.dev");
        clientePayload.put("cpf", "000.000.000-00");
        clientePayload.put("segmento", SEGMENTO_PLACEHOLDER);
        clientePayload.put("estagio", CustomerStage.NOVO_LEAD.name());
        clientePayload.put("ltv", BigDecimal.ZERO);
        clientePayload.put("cashback", BigDecimal.ZERO);
        clientePayload.put("tags", List.of());

        return buildPayload(automation, clientePayload, mensagem, true);
    }

    private Map<String, Object> buildPayload(CampaignAutomation automation, Map<String, Object> clientePayload,
            String mensagem, boolean teste) {
        Map<String, Object> automacaoPayload = new LinkedHashMap<>();
        automacaoPayload.put("id", automation.id());
        automacaoPayload.put("nome", automation.nome());
        automacaoPayload.put("gatilho", automation.gatilho().name());
        automacaoPayload.put("canal", automation.canal().name());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("automacao", automacaoPayload);
        payload.put("cliente", clientePayload);
        payload.put("mensagem", mensagem);
        payload.put("disparadoEm", Instant.now().toString());
        payload.put("origem", WEBHOOK_ORIGEM);
        if (teste) {
            payload.put("teste", true);
        }
        return payload;
    }

    private Map<String, Object> dataVariables() {
        ZonedDateTime agora = ZonedDateTime.now(ZONA_BRASIL);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hoje", DATA_PT_BR.format(agora));
        data.put("hora", HORA_PT_BR.format(agora));
        return data;
    }

    private String formatDataPtBr(Instant instant) {
        return DATA_PT_BR.format(instant.atZone(ZONA_BRASIL));
    }

    private String formatMoedaPtBr(BigDecimal valor) {
        java.text.NumberFormat formatter = java.text.NumberFormat.getNumberInstance(new Locale("pt", "BR"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(valor);
    }

    /** Dígitos apenas, com DDI 55 — aproxima o {@code toWhatsAppNumber} do mahal-admin. */
    private String toWhatsAppNumber(String contato) {
        if (contato == null) {
            return null;
        }
        String digits = contato.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 13) {
            return null;
        }
        return digits.startsWith("55") ? digits : "55" + digits;
    }

    private String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return nome;
        }
        int espaco = nome.indexOf(' ');
        return espaco < 0 ? nome : nome.substring(0, espaco);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelStatus> getChannelStatus() {
        EmailChannelStatus email = emailPort.channelStatus();
        return List.of(
                ChannelStatus.of(ChannelType.EMAIL, email.conectado(), email.provedor(), email.detalhe()),
                ChannelStatus.of(ChannelType.WHATSAPP, false, null,
                        "Integração de WhatsApp ainda não implementada"));
    }

    private Customer requireCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private Tag requireTag(Long tagId) {
        return tagRepository.findById(tagId)
                .orElseThrow(() -> new TagNotFoundException(tagId));
    }

    private CampaignAutomation requireAutomation(Long automationId) {
        return campaignAutomationRepository.findById(automationId)
                .orElseThrow(() -> new CampaignAutomationNotFoundException(automationId));
    }
}
