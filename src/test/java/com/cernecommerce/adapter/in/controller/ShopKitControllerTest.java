package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.KitBuilderDTOConverter;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException.Reason;
import com.cernecommerce.core.domain.exception.estoque.KitTemplateNotFoundException;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.KitTemplateStep;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ECM-F008 — mapeamento HTTP do montador público. Segurança de rota é do SecurityConfig. */
class ShopKitControllerTest {

    private MockMvc mockMvc;
    private KitBuilderUseCase kitBuilderUseCase;

    private static final KitTemplate KIT = new KitTemplate(7L, "Kit Mahal", null, null, new BigDecimal("10"),
            true, true, true, List.of(new KitTemplateStep(10L, "Bag", 0, 1L, true, 1)));

    @BeforeEach
    void setup() {
        kitBuilderUseCase = mock(KitBuilderUseCase.class);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(exceptionHandler, "lockoutDurationMinutes", 15L);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShopKitController(kitBuilderUseCase, mock(EstoqueUseCase.class),
                        new KitBuilderDTOConverter()))
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    @Test
    void list_returnsSellableKitsWithSteps() throws Exception {
        when(kitBuilderUseCase.listSellableTemplates(KitChannel.MARKETPLACE)).thenReturn(List.of(KIT));

        mockMvc.perform(get("/shop/kits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kit Mahal"))
                .andExpect(jsonPath("$[0].steps[0].id").value(10));
    }

    @Test
    void get_hiddenKitIs404() throws Exception {
        when(kitBuilderUseCase.getSellableTemplate(9L, KitChannel.MARKETPLACE))
                .thenThrow(new KitTemplateNotFoundException(9L));

        mockMvc.perform(get("/shop/kits/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("KIT_TEMPLATE_NOT_FOUND"));
    }

    @Test
    void quote_returnsTotals() throws Exception {
        when(kitBuilderUseCase.quote(any(), eq(KitChannel.MARKETPLACE))).thenReturn(new KitQuote(KIT, List.of(
                new KitQuote.Line(10L, "Bag", "BAG-01", "Bag", new BigDecimal("40.00"), new BigDecimal("4.00"))),
                new BigDecimal("40.00"), new BigDecimal("4.00"), new BigDecimal("36.00")));

        mockMvc.perform(post("/shop/kits/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":7,\"picks\":[{\"stepId\":10,\"sku\":\"BAG-01\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(36.00))
                .andExpect(jsonPath("$.lines[0].netAmount").value(36.00));
    }

    @Test
    void quote_invalidSelectionIs422WithRuleCode() throws Exception {
        when(kitBuilderUseCase.quote(any(), eq(KitChannel.MARKETPLACE))).thenThrow(
                new InvalidKitSelectionException(Reason.KIT_REQUIRED_STEP_MISSING, "Passo obrigatório sem escolha: Bag"));

        mockMvc.perform(post("/shop/kits/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateId\":7,\"picks\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("KIT_REQUIRED_STEP_MISSING"));
    }

    @Test
    void quote_withoutTemplateIdIs400() throws Exception {
        mockMvc.perform(post("/shop/kits/quote").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"picks\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
