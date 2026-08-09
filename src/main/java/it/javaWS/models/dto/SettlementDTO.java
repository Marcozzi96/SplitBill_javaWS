package it.javaWS.models.dto;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class SettlementDTO {

    private UserDTO debtor;
    private UserDTO creditor;
    private BigDecimal amount;

    public SettlementDTO(UserDTO debtor, UserDTO creditor, BigDecimal amount) {
        this.debtor = debtor;
        this.creditor = creditor;
        this.amount = amount;
    }
}
