package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("dev")
class NfeImportControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** CNPJ que não existe em nenhum fornecedor seedado em dev — o suficiente para exercitar 404. */
    private static final String NFE_UNKNOWN_SUPPLIER = """
            <nfeProc><NFe><infNFe>
              <emit><CNPJ>00000000000000</CNPJ></emit>
              <det nItem="1"><prod><cProd>X</cProd><xProd>Y</xProd><qCom>1</qCom><vUnCom>1.00</vUnCom></prod></det>
            </infNFe></NFe></nfeProc>
            """;

    private static final String CONFIRM_BODY =
            "{\"nfeImportId\":999999,\"warehouseCode\":\"LOJA-01\",\"overrides\":[]}";

    // ── Preview ──────────────────────────────────────────────────────────────────────────────

    @Test
    void preview_without_auth_returns_401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "nfe.xml", MediaType.APPLICATION_XML_VALUE,
                NFE_UNKNOWN_SUPPLIER.getBytes());

        mockMvc.perform(multipart("/compras/goods-receipts/nfe-preview").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void preview_without_compras_receipt_manage_returns_403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "nfe.xml", MediaType.APPLICATION_XML_VALUE,
                NFE_UNKNOWN_SUPPLIER.getBytes());

        mockMvc.perform(multipart("/compras/goods-receipts/nfe-preview").file(file)
                        .with(user("bob").authorities(new SimpleGrantedAuthority("COMPRAS_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void preview_with_permission_and_unknown_supplier_returns_404() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "nfe.xml", MediaType.APPLICATION_XML_VALUE,
                NFE_UNKNOWN_SUPPLIER.getBytes());

        mockMvc.perform(multipart("/compras/goods-receipts/nfe-preview").file(file)
                        .with(user("comprador").authorities(new SimpleGrantedAuthority("COMPRAS_RECEIPT_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SUPPLIER_NOT_FOUND_BY_TAX_ID"));
    }

    // ── Confirm ──────────────────────────────────────────────────────────────────────────────

    @Test
    void confirm_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/compras/goods-receipts/nfe-confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirm_without_compras_receipt_manage_returns_403() throws Exception {
        mockMvc.perform(post("/compras/goods-receipts/nfe-confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_BODY)
                        .with(user("bob").authorities(new SimpleGrantedAuthority("COMPRAS_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirm_with_permission_and_nonexistent_import_returns_404() throws Exception {
        mockMvc.perform(post("/compras/goods-receipts/nfe-confirm")
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRM_BODY)
                        .with(user("comprador").authorities(new SimpleGrantedAuthority("COMPRAS_RECEIPT_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NFE_IMPORT_NOT_FOUND"));
    }
}
