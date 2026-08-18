package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.compras.NfeImportAlreadyProcessedException;
import com.cernecommerce.core.domain.exception.compras.NfeImportNotFoundException;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundByTaxIdException;
import com.cernecommerce.core.domain.exception.compras.UnmatchedNfeLineException;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.domain.model.compras.NfeImportPreview;
import com.cernecommerce.core.domain.model.compras.NfeImportStatus;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import com.cernecommerce.core.ports.in.NfeImportUseCase;
import com.cernecommerce.core.ports.out.compras.NfeImportRepository;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import com.cernecommerce.core.ports.out.estoque.NfeXmlImportPort;
import com.cernecommerce.core.ports.out.storage.NfeImportStoragePort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Importação de entrada de mercadoria por XML de NF-e (EST-F005). Ver o javadoc de
 * {@link NfeImportUseCase} para o porquê do fluxo em duas fases.
 */
public class NfeImportService implements NfeImportUseCase {

    private final NfeXmlImportPort nfeXmlImportPort;
    private final SupplierRepository supplierRepository;
    private final NfeImportStoragePort nfeImportStoragePort;
    private final NfeImportRepository nfeImportRepository;
    private final ComprasUseCase comprasUseCase;

    public NfeImportService(NfeXmlImportPort nfeXmlImportPort, SupplierRepository supplierRepository,
            NfeImportStoragePort nfeImportStoragePort, NfeImportRepository nfeImportRepository,
            ComprasUseCase comprasUseCase) {
        this.nfeXmlImportPort = nfeXmlImportPort;
        this.supplierRepository = supplierRepository;
        this.nfeImportStoragePort = nfeImportStoragePort;
        this.nfeImportRepository = nfeImportRepository;
        this.comprasUseCase = comprasUseCase;
    }

    @Override
    @Transactional
    public NfeImport previewImport(byte[] xmlBytes, String username) {
        // Parsing primeiro: XML malformado não gera nenhuma trilha — não há nada de auditável
        // ainda. A partir daqui o XML é reconhecível como NF-e, então vale persistir mesmo que o
        // fornecedor não seja encontrado (REJECTED é justamente esse registro).
        NfeImportPreview preview = nfeXmlImportPort.parse(xmlBytes);
        String fileReference = nfeImportStoragePort.save(xmlBytes, "xml");

        return supplierRepository.findByTaxId(preview.emitterCnpj())
                .map((Supplier supplier) -> nfeImportRepository.save(NfeImport.previewed(supplier.id(),
                        preview.emitterCnpj(), fileReference, preview.lines(), username)))
                .orElseGet(() -> {
                    // Persiste o REJECTED antes de lançar — a rejeição em si é o que fica
                    // auditado; sem isso, toda tentativa de import de fornecedor desconhecido
                    // desapareceria sem rastro.
                    nfeImportRepository.save(NfeImport.rejected(preview.emitterCnpj(), fileReference,
                            preview.lines(), username));
                    throw new SupplierNotFoundByTaxIdException(preview.emitterCnpj());
                });
    }

    @Override
    @Transactional
    public GoodsReceipt confirmImport(NfeImportConfirmCommand command, String username) {
        NfeImport nfeImport = nfeImportRepository.findById(command.nfeImportId())
                .orElseThrow(() -> new NfeImportNotFoundException(command.nfeImportId()));
        if (nfeImport.status() != NfeImportStatus.PREVIEWED) {
            throw new NfeImportAlreadyProcessedException(nfeImport.id(), nfeImport.status());
        }

        Map<Integer, String> overridesByItem = new HashMap<>();
        for (LineOverride override : command.overrides()) {
            overridesByItem.put(override.itemNumber(), override.sku());
        }

        List<NfeImportLine> resolvedLines = new ArrayList<>(nfeImport.lines().size());
        for (NfeImportLine line : nfeImport.lines()) {
            String override = overridesByItem.get(line.itemNumber());
            resolvedLines.add(override != null ? line.withMatchedSku(override) : line);
        }
        NfeImport withResolvedLines = nfeImport.withLines(resolvedLines);

        // Toda linha precisa ter SKU resolvido ANTES de tocar em receiveGoods — mesma disciplina
        // de "validar tudo antes de mexer em estoque" de PdvService/ComandaService.
        List<Integer> stillUnmatched = withResolvedLines.unmatchedLines().stream()
                .map(NfeImportLine::itemNumber).toList();
        if (!stillUnmatched.isEmpty()) {
            throw new UnmatchedNfeLineException(stillUnmatched);
        }

        List<GoodsReceiptItem> items = resolvedLines.stream()
                .map(line -> new GoodsReceiptItem(line.matchedSku(), line.quantity(), line.lotCode(),
                        line.expiryDate(), line.unitPrice()))
                .toList();

        GoodsReceipt receipt = comprasUseCase.receiveGoods(withResolvedLines.supplierId(),
                command.warehouseCode(), items, username);

        nfeImportRepository.save(
                withResolvedLines.confirmed(command.warehouseCode(), receipt.id(), Instant.now()));
        return receipt;
    }
}
