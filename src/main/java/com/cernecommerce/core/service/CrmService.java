package com.cernecommerce.core.service;

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
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.crm.StageTransitionRepository;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CrmService implements CrmUseCase {

    // Placeholder até os domínios de pedidos/cashback e de campanhas existirem —
    // ver crm/listagem-clientes-rfm e crm/automacoes-campanhas.
    private static final BigDecimal LTV_MEDIO_PLACEHOLDER = BigDecimal.ZERO;
    private static final long DISPAROS_WHATSAPP_PLACEHOLDER = 0L;
    private static final String SEGMENTO_PLACEHOLDER = "NOVO";

    private final CustomerRepository customerRepository;
    private final CustomerNoteRepository customerNoteRepository;
    private final StageTransitionRepository stageTransitionRepository;
    private final TagRepository tagRepository;
    private final CustomerTagRepository customerTagRepository;
    private final CampaignAutomationRepository campaignAutomationRepository;
    private final CampaignLogRepository campaignLogRepository;
    private final EmailPort emailPort;

    public CrmService(CustomerRepository customerRepository, CustomerNoteRepository customerNoteRepository,
            StageTransitionRepository stageTransitionRepository, TagRepository tagRepository,
            CustomerTagRepository customerTagRepository, CampaignAutomationRepository campaignAutomationRepository,
            CampaignLogRepository campaignLogRepository, EmailPort emailPort) {
        this.customerRepository = customerRepository;
        this.customerNoteRepository = customerNoteRepository;
        this.stageTransitionRepository = stageTransitionRepository;
        this.tagRepository = tagRepository;
        this.customerTagRepository = customerTagRepository;
        this.campaignAutomationRepository = campaignAutomationRepository;
        this.campaignLogRepository = campaignLogRepository;
        this.emailPort = emailPort;
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
            CampaignChannel canal, String template) {
        return campaignAutomationRepository.save(
                CampaignAutomation.create(nome, gatilho, segmentoAlvo, canal, template));
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
                .map(customer -> campaignLogRepository.save(CampaignLogEntry.create(automationId, customer.id())))
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
