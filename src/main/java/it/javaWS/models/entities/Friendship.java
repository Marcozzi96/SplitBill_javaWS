package it.javaWS.models.entities;

import java.time.LocalDateTime;

import org.hibernate.annotations.Check;

import it.javaWS.enums.StatoAmicizia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Check(constraints = "user1_id < user2_id")
@Table(name = "friendship", uniqueConstraints = @UniqueConstraint(columnNames = {"user1_id", "user2_id"}))
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;
    
    @ManyToOne
    @JoinColumn(name = "user_tobe_confirmed_id", nullable = false)
    private User userToBeConfirmed;
    
    @Column(name = "messaggio", nullable = true)
    private String messaggio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatoAmicizia stato;  // Enum: IN_ATTESA, ACCETTATA, RIFIUTATA

    @Column(name = "data_richiesta", nullable = false)
    private LocalDateTime dataRichiesta;

    // Getters, setters...
}
