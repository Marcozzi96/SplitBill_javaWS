package it.javaWS.models.dto;

import java.math.BigDecimal;

import it.javaWS.models.enums.SettlementDirection;
import lombok.Data;

@Data
public class UserSettlementDTO {

    private UserDTO counterparty;
    private BigDecimal amount;
    private SettlementDirection direction;

    public UserSettlementDTO(UserDTO counterparty, BigDecimal amount, SettlementDirection direction) {
        this.counterparty = counterparty;
        this.amount = amount;
        this.direction = direction;
    }
}
