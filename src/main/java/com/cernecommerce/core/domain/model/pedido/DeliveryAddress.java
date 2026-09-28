package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;

/**
 * Endereço de uma {@link DeliveryType#ENTREGA} (PDV-F022), congelado no pedido — o cliente mudar
 * de endereço depois não reescreve para onde a venda foi.
 */
public record DeliveryAddress(
        String street,
        String number,
        String complement,
        String zipCode,
        String district,
        String city,
        String state,
        String country,
        String reference) {

    public DeliveryAddress {
        street = blankToNull(street);
        number = blankToNull(number);
        complement = blankToNull(complement);
        zipCode = blankToNull(zipCode);
        district = blankToNull(district);
        city = blankToNull(city);
        state = blankToNull(state);
        country = blankToNull(country);
        reference = blankToNull(reference);
        if (street == null || number == null || city == null || state == null) {
            throw new InvalidDeliveryException("endereço de entrega exige street, number, city e state");
        }
    }

    /** Sobrepõe os campos não nulos de {@code patch}; campo em branco apaga o valor. */
    public DeliveryAddress merge(DeliveryAddress.Patch patch) {
        if (patch == null) {
            return this;
        }
        return new DeliveryAddress(
                pick(patch.street(), street), pick(patch.number(), number), pick(patch.complement(), complement),
                pick(patch.zipCode(), zipCode), pick(patch.district(), district), pick(patch.city(), city),
                pick(patch.state(), state), pick(patch.country(), country), pick(patch.reference(), reference));
    }

    /** Edição parcial do endereço: {@code null} mantém o valor atual. */
    public record Patch(String street, String number, String complement, String zipCode, String district,
            String city, String state, String country, String reference) {

        /** Endereço completo a partir do patch — usado quando o pedido ainda não tinha endereço. */
        DeliveryAddress toAddress() {
            return new DeliveryAddress(street, number, complement, zipCode, district, city, state, country,
                    reference);
        }
    }

    static String pick(String patched, String current) {
        return patched == null ? current : patched;
    }

    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
