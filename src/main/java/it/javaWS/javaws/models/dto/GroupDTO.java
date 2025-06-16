package it.javaWS.javaws.models.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import it.javaWS.javaws.models.entities.Bill;
import it.javaWS.javaws.models.entities.Group;
import it.javaWS.javaws.models.entities.User;
import lombok.Data;

@Data
public class GroupDTO {
    private Long groupId;
    private String name;
    private String description;
    private LocalDate creationDate;
    private Set<UserDTO> users;
    private Set<BillDTO> bills;

    public GroupDTO(Group group) {
    	this.setGroupId(group.getId());
    	this.setName(group.getName());
    	this.setDescription(group.getDescription());
    	this.setCreationDate(group.getCreationDate());
    	
    }
    
    public GroupDTO setUsers(Set<User> users) {
    	this.users = users
        		.stream()
        		.map(u-> new UserDTO(u))
        		.collect(Collectors.toSet());
		return this;
    }
    public GroupDTO setBills(Set<Bill> bills) {
    	this.bills = bills.stream()
        		.map(bill->new BillDTO(bill))
        		.collect(Collectors.toSet());
		return this;
    }
    
}

