package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.GoodsReceiptDTOConverter;
import com.cernecommerce.adapter.in.converter.NfeImportDTOConverter;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundByTaxIdException;
import com.cernecommerce.core.domain.exception.compras.UnmatchedNfeLineException;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.domain.model.compras.NfeImportStatus;
import com.cernecommerce.core.ports.in.NfeImportUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

class NfeImportControllerTest {

    private MockMvc mockMvc;
    private NfeImportUseCase nfeImportUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("comprador1", null, List.of());

    @BeforeEach
    void setup() {
        nfeImportUseCase = mock(NfeImportUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NfeImportController(nfeImportUseCase, new NfeImportDTOConverter(),
                        new GoodsReceiptDTOConverter(), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static NfeImportLine matchedLine() {
        return NfeImportLine.fromXml(1, "FORN-001", "7891234567890", "Essência Menta", BigDecimal.ONE,
                new BigDecimal("10.00"), null, null, "ESS-MENTA-50");
    }

    @Test
    void previewImport_returns_200_withParsedLines() throws Exception {
        NfeImport preview = NfeImport.of(1L, 7L, "12345678000199", null, "uuid.xml", NfeImportStatus.PREVIEWED,
                null, List.of(matchedLine()), "comprador1", Instant.now(), null);
        when(nfeImportUseCase.previewImport(any(), anyString())).thenReturn(preview);

        MockMultipartFile file = new MockMultipartFile("file", "nfe.xml", MediaType.APPLICATION_XML_VALUE,
                "<nfeProc/>".getBytes());

        mockMvc.perform(multipart("/compras/goods-receipts/nfe-preview").file(file).principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREVIEWED"))
                .andExpect(jsonPath("$.lines[0].matchedSku").value("ESS-MENTA-50"));
    }

    @Test
    void previewImport_supplierNotFound_returns_404() throws Exception {
        when(nfeImportUseCase.previewImport(any(), anyString()))
                .thenThrow(new SupplierNotFoundByTaxIdException("00000000000000"));

        MockMultipartFile file = new MockMultipartFile("file", "nfe.xml", MediaType.APPLICATION_XML_VALUE,
                "<nfeProc/>".getBytes());

        mockMvc.perform(multipart("/compras/goods-receipts/nfe-preview").file(file).principal(AUTH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SUPPLIER_NOT_FOUND_BY_TAX_ID"));
    }

    @Test
    void previewImport_emptyFile_returns_400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "nfe.xml", MediaType.APPLICATION_XML_VALUE,
                new byte[0]);

        mockMvc.perform(multipart("/compras/goods-receipts/nfe-preview").file(empty).principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_NFE_XML"));
    }

    @Test
    void confirmImport_returns_201_withGoodsReceipt() throws Exception {
        GoodsReceipt receipt = GoodsReceipt.of(55L, 7L, "LOJA-01",
                List.of(new GoodsReceiptItem("ESS-MENTA-50", BigDecimal.ONE, null, null, new BigDecimal("10.00"))),
                "comprador1", Instant.now());
        when(nfeImportUseCase.confirmImport(any(), anyString())).thenReturn(receipt);

        mockMvc.perform(post("/compras/goods-receipts/nfe-confirm")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nfeImportId\":1,\"warehouseCode\":\"LOJA-01\",\"overrides\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55));
    }

    @Test
    void confirmImport_unmatchedLine_returns_400() throws Exception {
        when(nfeImportUseCase.confirmImport(any(), anyString()))
                .thenThrow(new UnmatchedNfeLineException(List.of(2)));

        mockMvc.perform(post("/compras/goods-receipts/nfe-confirm")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nfeImportId\":1,\"warehouseCode\":\"LOJA-01\",\"overrides\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNMATCHED_NFE_LINE"));
    }
}
