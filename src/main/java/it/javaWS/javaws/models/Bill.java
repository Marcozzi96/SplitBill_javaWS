package it.javaWS.javaws.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@Entity
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;
    private LocalDate date;
    private BigDecimal amount;
    private String notes;
    
    @ManyToMany@JoinTable( name = "debtors_bill", joinColumns = @JoinColumn(name = "debtor_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "bill_id", referencedColumnName = "id"))
    private Set<User> debtors;
    
    @ManyToOne
    @JoinColumn(name = "buyer_id")
    private User buyer;

    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;

    @OneToMany(mappedBy = "bill")
    private List<Transaction> transactions;
}
