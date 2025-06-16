package it.javaWS.javaws.models.dto;

import java.math.BigDecimal;

import lombok.Data;
@Data
public class UserBalanceDTO {
    private Long userId;
    private String username;
    private BigDecimal totalPaid;
    private BigDecimal totalOwed;
    private BigDecimal netBalance;

    public UserBalanceDTO(Long userId, String username,
                          BigDecimal totalPaid, BigDecimal totalOwed) {
        this.userId = userId;
        this.username = username;
        this.totalPaid = totalPaid;
        this.totalOwed = totalOwed;
        this.netBalance = totalPaid.subtract(totalOwed);
    }


}
