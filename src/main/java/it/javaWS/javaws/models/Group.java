package it.javaWS.javaws.models;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Entity
@Table(name = "groups")
public class Group {

	@EqualsAndHashCode.Include
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private LocalDate creationDate;
    private String description;
    
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserGroup> userGroups;

    @OneToMany(mappedBy = "group")
    private List<Bill> bills;

    @OneToMany(mappedBy = "group")
    private List<Transaction> transactions;
    
    public Set<User> getUsers(){
    	return userGroups.stream().map(ug->ug.getUser()).collect(Collectors.toSet());
    }
}
