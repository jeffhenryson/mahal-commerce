package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.CampaignAutomationNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.crm.DuplicateCustomerEmailException;
import com.cernecommerce.core.domain.exception.crm.DuplicateTagNameException;
import com.cernecommerce.core.domain.exception.crm.TagNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignDispatchStatus;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.CrmDashboardOverview;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.StageTransition;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.crm.StageTransitionRepository;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CrmServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock CustomerNoteRepository customerNoteRepository;
    @Mock StageTransitionRepository stageTransitionRepository;
    @Mock TagRepository tagRepository;
    @Mock CustomerTagRepository customerTagRepository;
    @Mock CampaignAutomationRepository campaignAutomationRepository;
    @Mock CampaignLogRepository campaignLogRepository;

    CrmService crmService;

    @BeforeEach
    void setUp() {
        crmService = new CrmService(customerRepository, customerNoteRepository, stageTransitionRepository,
                tagRepository, customerTagRepository, campaignAutomationRepository, campaignLogRepository);
    }

    private Customer customer(Long id, String email) {
        return Customer.of(id, "Maria Silva", "11999998888", email, null, null, Instant.now(),
                CustomerStage.NOVO_LEAD);
    }

    private Customer customer(Long id, String email, CustomerStage estagio) {
        return Customer.of(id, "Maria Silva", "11999998888", email, null, null, Instant.now(), estagio);
    }

    @Test
    void createCustomer_savesAndReturns() {
        Customer saved = customer(1L, "maria@example.com");
        when(customerRepository.findByEmail("maria@example.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any())).thenReturn(saved);

        Customer result = crmService.createCustomer("Maria Silva", "11999998888", "maria@example.com",
                "12345678900", "loja-fisica");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("maria@example.com");
        verify(customerRepository).save(any());
    }

    @Test
    void createCustomer_throwsWhenEmailAlreadyExists() {
        when(customerRepository.findByEmail("maria@example.com"))
                .thenReturn(Optional.of(customer(1L, "maria@example.com")));

        assertThatThrownBy(() -> crmService.createCustomer("Maria Silva", "11999998888", "maria@example.com",
                null, null))
                .isInstanceOf(DuplicateCustomerEmailException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void findCustomerById_returnsCustomer() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));

        Customer result = crmService.findCustomerById(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void findCustomerById_throwsWhenNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.findCustomerById(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void listCustomers_delegatesToRepository() {
        PageResult<Customer> page = new PageResult<>(List.of(customer(1L, "maria@example.com")), 0, 20, 1L, 1);
        when(customerRepository.findAll("maria", 0, 20)).thenReturn(page);

        PageResult<Customer> result = crmService.listCustomers("maria", 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void listCustomers_allowsNullSearch() {
        PageResult<Customer> page = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(customerRepository.findAll(null, 0, 20)).thenReturn(page);

        PageResult<Customer> result = crmService.listCustomers(null, 0, 20);

        assertThat(result.content()).isEmpty();
    }

    @Test
    void listCustomersForExport_delegatesToRepository() {
        when(customerRepository.findAllForExport("maria"))
                .thenReturn(List.of(customer(1L, "maria@example.com")));

        List<Customer> result = crmService.listCustomersForExport("maria");

        assertThat(result).hasSize(1);
    }

    @Test
    void addNote_savesAndReturnsWhenCustomerExists() {
        CustomerNote saved = CustomerNote.of(10L, 1L, "gerente", "Nota", Instant.now());
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(customerNoteRepository.save(any())).thenReturn(saved);

        CustomerNote result = crmService.addNote(1L, "gerente", "Nota");

        assertThat(result.id()).isEqualTo(10L);
        verify(customerNoteRepository).save(any());
    }

    @Test
    void addNote_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.addNote(99L, "gerente", "Nota"))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(customerNoteRepository, never()).save(any());
    }

    @Test
    void listNotes_returnsNotesWhenCustomerExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(customerNoteRepository.findByCustomerId(1L))
                .thenReturn(List.of(CustomerNote.of(10L, 1L, "gerente", "Nota", Instant.now())));

        List<CustomerNote> result = crmService.listNotes(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listNotes_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.listNotes(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void moveStage_updatesCustomerAndRecordsTransitionWhenCustomerExists() {
        Customer existing = customer(1L, "maria@example.com", CustomerStage.NOVO_LEAD);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stageTransitionRepository.save(any()))
                .thenReturn(StageTransition.create(1L, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO, "gerente"));

        Customer result = crmService.moveStage(1L, CustomerStage.EM_ATENDIMENTO, "gerente");

        assertThat(result.estagio()).isEqualTo(CustomerStage.EM_ATENDIMENTO);
        verify(stageTransitionRepository).save(any());
        verify(customerRepository).save(any());
    }

    @Test
    void moveStage_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.moveStage(99L, CustomerStage.EM_ATENDIMENTO, "gerente"))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(stageTransitionRepository, never()).save(any());
    }

    @Test
    void moveStage_throwsWhenMovingToSameStage() {
        when(customerRepository.findById(1L))
                .thenReturn(Optional.of(customer(1L, "maria@example.com", CustomerStage.NOVO_LEAD)));

        assertThatThrownBy(() -> crmService.moveStage(1L, CustomerStage.NOVO_LEAD, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void listStageHistory_returnsHistoryWhenCustomerExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(stageTransitionRepository.findByCustomerId(1L)).thenReturn(
                List.of(StageTransition.create(1L, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO, "gerente")));

        List<StageTransition> result = crmService.listStageHistory(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listStageHistory_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.listStageHistory(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void getDashboardOverview_aggregatesRealCountsWithPlaceholders() {
        when(customerRepository.countAll()).thenReturn(10L);
        when(customerRepository.countActive()).thenReturn(7L);
        when(customerRepository.countByStage()).thenReturn(Map.of(
                CustomerStage.NOVO_LEAD, 5L,
                CustomerStage.EM_ATENDIMENTO, 2L,
                CustomerStage.INATIVO, 3L));

        CrmDashboardOverview result = crmService.getDashboardOverview();

        assertThat(result.totalClientes()).isEqualTo(10L);
        assertThat(result.clientesAtivos()).isEqualTo(7L);
        assertThat(result.porEstagio()).containsEntry(CustomerStage.NOVO_LEAD, 5L);
        assertThat(result.ltvMedio()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.disparosWhatsappMes()).isZero();
        assertThat(result.porSegmento()).containsEntry("NOVO", 10L);
    }

    @Test
    void createTag_savesAndReturns() {
        Tag saved = Tag.of(1L, "VIP");
        when(tagRepository.findByNome("VIP")).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenReturn(saved);

        Tag result = crmService.createTag("VIP");

        assertThat(result.id()).isEqualTo(1L);
        verify(tagRepository).save(any());
    }

    @Test
    void createTag_throwsWhenNameAlreadyExists() {
        when(tagRepository.findByNome("VIP")).thenReturn(Optional.of(Tag.of(1L, "VIP")));

        assertThatThrownBy(() -> crmService.createTag("VIP"))
                .isInstanceOf(DuplicateTagNameException.class);
        verify(tagRepository, never()).save(any());
    }

    @Test
    void listTags_delegatesToRepository() {
        when(tagRepository.findAllWithCustomerCount())
                .thenReturn(List.of(new TagSummary(1L, "VIP", 3L)));

        List<TagSummary> result = crmService.listTags();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).clientesCount()).isEqualTo(3L);
    }

    @Test
    void deleteTag_deletesWhenExists() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.of(1L, "VIP")));

        crmService.deleteTag(1L);

        verify(tagRepository).deleteById(1L);
    }

    @Test
    void deleteTag_throwsWhenNotFound() {
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.deleteTag(99L))
                .isInstanceOf(TagNotFoundException.class);
        verify(tagRepository, never()).deleteById(any());
    }

    @Test
    void addTagToCustomer_associatesWhenBothExist() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.of(1L, "VIP")));

        crmService.addTagToCustomer(1L, 1L);

        verify(customerTagRepository).associate(1L, 1L);
    }

    @Test
    void addTagToCustomer_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.addTagToCustomer(99L, 1L))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(customerTagRepository, never()).associate(any(), any());
    }

    @Test
    void addTagToCustomer_throwsWhenTagNotFound() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.addTagToCustomer(1L, 99L))
                .isInstanceOf(TagNotFoundException.class);
        verify(customerTagRepository, never()).associate(any(), any());
    }

    @Test
    void removeTagFromCustomer_disassociatesWhenBothExist() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(tagRepository.findById(1L)).thenReturn(Optional.of(Tag.of(1L, "VIP")));

        crmService.removeTagFromCustomer(1L, 1L);

        verify(customerTagRepository).disassociate(1L, 1L);
    }

    @Test
    void listCustomerTags_returnsTagsWhenCustomerExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(1L, "maria@example.com")));
        when(customerTagRepository.findTagsByCustomerId(1L)).thenReturn(List.of(Tag.of(1L, "VIP")));

        List<Tag> result = crmService.listCustomerTags(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listCustomerTags_throwsWhenCustomerNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.listCustomerTags(99L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    private CampaignAutomation automation(Long id, boolean ativa) {
        return CampaignAutomation.of(id, "Boas-vindas", CampaignTrigger.MANUAL, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Ola {nome}", ativa, Instant.now());
    }

    @Test
    void createAutomation_savesAndReturns() {
        CampaignAutomation saved = automation(1L, true);
        when(campaignAutomationRepository.save(any())).thenReturn(saved);

        CampaignAutomation result = crmService.createAutomation("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}");

        assertThat(result.id()).isEqualTo(1L);
        verify(campaignAutomationRepository).save(any());
    }

    @Test
    void listAutomations_delegatesToRepository() {
        when(campaignAutomationRepository.findAll()).thenReturn(List.of(automation(1L, true)));

        List<CampaignAutomation> result = crmService.listAutomations();

        assertThat(result).hasSize(1);
    }

    @Test
    void setAutomationActive_updatesFlagWhenExists() {
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation(1L, true)));
        when(campaignAutomationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CampaignAutomation result = crmService.setAutomationActive(1L, false);

        assertThat(result.ativa()).isFalse();
    }

    @Test
    void setAutomationActive_throwsWhenNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.setAutomationActive(99L, false))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
    }

    @Test
    void deleteAutomation_deletesWhenExists() {
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation(1L, true)));

        crmService.deleteAutomation(1L);

        verify(campaignAutomationRepository).deleteById(1L);
    }

    @Test
    void deleteAutomation_throwsWhenNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.deleteAutomation(99L))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
        verify(campaignAutomationRepository, never()).deleteById(any());
    }

    @Test
    void dispatchAutomation_createsOneLogEntryPerTargetCustomer() {
        CampaignAutomation automation = automation(1L, true);
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation));
        when(customerRepository.findByEstagio(CustomerStage.NOVO_LEAD)).thenReturn(
                List.of(customer(1L, "maria@example.com"), customer(2L, "joao@example.com")));
        when(campaignLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<CampaignLogEntry> result = crmService.dispatchAutomation(1L);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(entry -> {
            assertThat(entry.status()).isEqualTo(CampaignDispatchStatus.PENDENTE_INTEGRACAO);
            assertThat(entry.convertidoEm()).isNull();
        });
        verify(campaignLogRepository, times(2)).save(any());
    }

    @Test
    void dispatchAutomation_throwsWhenAutomationNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.dispatchAutomation(99L))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
    }

    @Test
    void listAutomationLog_returnsLogWhenAutomationExists() {
        when(campaignAutomationRepository.findById(1L)).thenReturn(Optional.of(automation(1L, true)));
        when(campaignLogRepository.findByAutomationId(1L)).thenReturn(
                List.of(CampaignLogEntry.create(1L, 10L)));

        List<CampaignLogEntry> result = crmService.listAutomationLog(1L);

        assertThat(result).hasSize(1);
    }

    @Test
    void listAutomationLog_throwsWhenAutomationNotFound() {
        when(campaignAutomationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> crmService.listAutomationLog(99L))
                .isInstanceOf(CampaignAutomationNotFoundException.class);
    }
}
