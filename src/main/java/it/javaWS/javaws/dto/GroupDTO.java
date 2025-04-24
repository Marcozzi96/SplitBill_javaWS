package it.javaWS.javaws.dto;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import it.javaWS.javaws.models.Group;
import lombok.Data;

@Data
public class GroupDTO {
    private Long id;
    private String name;
    private String description;
    private LocalDate creationDate;
    private Set<UserDTO> users;
    private Set<BillDTO> bills;

    public GroupDTO(Group group) {
    	this.setId(group.getId());
    	this.setName(group.getName());
    	this.setDescription(group.getDescription());
    	this.setCreationDate(group.getCreationDate());
    	
    }
    
    public GroupDTO setUsers(Group group) {
    	this.users = group.getUserGroups()
        		.stream()
        		.map(u-> new UserDTO(u.getUser()))
        		.collect(Collectors.toSet());
		return this;
    }
    public GroupDTO setBills(Group group) {
    	this.bills = group.getBills()
    			.stream()
        		.map(bill->new BillDTO(bill))
        		.collect(Collectors.toSet());
		return this;
    }
    
}

