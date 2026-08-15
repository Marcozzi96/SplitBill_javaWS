package it.javaWS.models.dto;

import java.math.BigDecimal;

import it.javaWS.models.enums.SettlementDirection;
import lombok.Data;

@Data
public class UserSettlementDTO {

    private UserDTO counterparty;
    private BigDecimal amount;
    private SettlementDirection direction;
    // Gruppo a cui si riferisce il debito/credito; null = debito personale (fuori dai gruppi,
    // tipicamente trasferito quando un utente è uscito dal gruppo).
    private Long groupId;
    private String groupName;

    public UserSettlementDTO(UserDTO counterparty, BigDecimal amount, SettlementDirection direction,
            Long groupId, String groupName) {
        this.counterparty = counterparty;
        this.amount = amount;
        this.direction = direction;
        this.groupId = groupId;
        this.groupName = groupName;
    }
}
