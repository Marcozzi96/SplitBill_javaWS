package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.javaWS.models.dto.ReorderShoppingItemRequest;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.ShoppingItem;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.enums.GroupRole;
import it.javaWS.repositories.GroupRepository;
import it.javaWS.repositories.ShoppingItemRepository;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.repositories.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ShoppingItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private ShoppingItemRepository shoppingItemRepository;

    @Test
    void createItem_success_returnsOkAndPosition() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(post("/shopping-items/new")
                        .with(user(member))
                        .param("groupId", group.getId().toString())
                        .param("name", "  Pane  ")
                        .param("note", "integrale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").isNumber())
                .andExpect(jsonPath("$.groupId").value(group.getId()))
                .andExpect(jsonPath("$.name").value("Pane"))
                .andExpect(jsonPath("$.note").value("integrale"))
                .andExpect(jsonPath("$.toBuy").value(true))
                .andExpect(jsonPath("$.position").value(1000.0))
                .andExpect(jsonPath("$.createdAt").isString());
    }

    @Test
    void createItem_asNonMember_returnsForbidden() throws Exception {
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Casa");

        mockMvc.perform(post("/shopping-items/new")
                        .with(user(outsider))
                        .param("groupId", group.getId().toString())
                        .param("name", "Pane"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createItem_duplicateName_returnsBadRequest() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        createItem(group, "Pane", null, true, 1000.0);

        mockMvc.perform(post("/shopping-items/new")
                        .with(user(member))
                        .param("groupId", group.getId().toString())
                        .param("name", "Pane"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Articolo già presente in lista"));
    }

    @Test
    void createItem_duplicateDifferentCaseAndSpaces_returnsBadRequest() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        // Il duplicato vale anche per gli articoli già acquistati.
        ShoppingItem acquistato = createItem(group, "Pane", null, false, 1000.0);
        shoppingItemRepository.save(acquistato);

        mockMvc.perform(post("/shopping-items/new")
                        .with(user(member))
                        .param("groupId", group.getId().toString())
                        .param("name", "  PANE "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getItems_paginationAndOrdering_activeItemsFirst() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem a = createItem(group, "Pane", null, true, 1000.0);
        ShoppingItem b = createItem(group, "Latte", null, true, 2000.0);
        ShoppingItem c = createItem(group, "Pasta", null, true, 1500.0);
        // b acquistato: deve finire in fondo.
        b.setToBuy(false);
        shoppingItemRepository.save(b);

        mockMvc.perform(get("/shopping-items/group/{groupId}", group.getId())
                        .with(user(member))
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                // Ordinamento: toBuy true prima, poi position DESC, poi id ASC.
                .andExpect(jsonPath("$.content[0].itemId").value(c.getId()))
                .andExpect(jsonPath("$.content[0].toBuy").value(true))
                .andExpect(jsonPath("$.content[0].position").value(1500.0))
                .andExpect(jsonPath("$.content[1].itemId").value(a.getId()))
                .andExpect(jsonPath("$.content[1].toBuy").value(true))
                .andExpect(jsonPath("$.content[1].position").value(1000.0))
                .andExpect(jsonPath("$.content[2].itemId").value(b.getId()))
                .andExpect(jsonPath("$.content[2].toBuy").value(false))
                .andExpect(jsonPath("$.content[2].position").value(2000.0));

        // Paginazione: prima pagina da 2, seconda con l'acquistato.
        mockMvc.perform(get("/shopping-items/group/{groupId}", group.getId())
                        .with(user(member))
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].itemId").value(b.getId()));
    }

    @Test
    void getItems_toBuyFilter_returnsOnlyMatching() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        createItem(group, "Pane", null, true, 1000.0);
        ShoppingItem b = createItem(group, "Latte", null, true, 500.0);
        b.setToBuy(false);
        shoppingItemRepository.save(b);

        mockMvc.perform(get("/shopping-items/group/{groupId}", group.getId())
                        .with(user(member))
                        .param("toBuy", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Pane"));

        mockMvc.perform(get("/shopping-items/group/{groupId}", group.getId())
                        .with(user(member))
                        .param("toBuy", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Latte"));
    }

    @Test
    void getItems_asNonMember_returnsForbidden() throws Exception {
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Casa");

        mockMvc.perform(get("/shopping-items/group/{groupId}", group.getId())
                        .with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void toggleItem_bothDirections_returnsOkAndPreservesPosition() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem item = createItem(group, "Pane", null, true, 1234.0);

        mockMvc.perform(put("/shopping-items/{id}", item.getId())
                        .with(user(member))
                        .param("toBuy", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toBuy").value(false))
                .andExpect(jsonPath("$.position").value(1234.0));

        mockMvc.perform(put("/shopping-items/{id}", item.getId())
                        .with(user(member))
                        .param("toBuy", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toBuy").value(true))
                .andExpect(jsonPath("$.position").value(1234.0));
    }

    @Test
    void toggleItem_asNonMember_returnsForbidden() throws Exception {
        User member = createUser("member", "member@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem item = createItem(group, "Pane", null, true, 1000.0);

        mockMvc.perform(put("/shopping-items/{id}", item.getId())
                        .with(user(outsider))
                        .param("toBuy", "false"))
                .andExpect(status().isForbidden());
    }

    @Test
    void toggleItem_notFound_returnsNotFound() throws Exception {
        User member = createUser("member", "member@example.com");

        mockMvc.perform(put("/shopping-items/{id}", 9999L)
                        .with(user(member))
                        .param("toBuy", "false"))
                .andExpect(status().isNotFound());
    }

    @Test
    void reorderItem_success_betweenNeighbors() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem item = createItem(group, "Pane", null, true, 1000.0);
        ShoppingItem prev = createItem(group, "Latte", null, true, 2000.0);
        ShoppingItem next = createItem(group, "Pasta", null, true, 1000.0);

        ReorderShoppingItemRequest request = new ReorderShoppingItemRequest();
        request.setPrevItemId(prev.getId());
        request.setNextItemId(next.getId());

        mockMvc.perform(patch("/shopping-items/{itemId}/position", item.getId())
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[?(@.itemId == %d)].position".formatted(item.getId())).value(1500.0));
    }

    @Test
    void reorderItem_toTopAndBottom() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem top = createItem(group, "Pane", null, true, 2000.0);
        ShoppingItem item = createItem(group, "Latte", null, true, 1000.0);

        // Sposta in cima (prev null, next = top).
        ReorderShoppingItemRequest toTop = new ReorderShoppingItemRequest();
        toTop.setNextItemId(top.getId());

        mockMvc.perform(patch("/shopping-items/{itemId}/position", item.getId())
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toTop)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.itemId == %d)].position".formatted(item.getId())).value(2001.0));

        // Sposta in fondo (prev = top, next null).
        ReorderShoppingItemRequest toBottom = new ReorderShoppingItemRequest();
        toBottom.setPrevItemId(top.getId());

        mockMvc.perform(patch("/shopping-items/{itemId}/position", item.getId())
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(toBottom)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.itemId == %d)].position".formatted(item.getId())).value(1999.0));
    }

    @Test
    void reorderItem_alreadyBought_returnsBadRequest() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem item = createItem(group, "Pane", null, false, 1000.0);

        ReorderShoppingItemRequest request = new ReorderShoppingItemRequest();
        request.setPrevItemId(null);
        request.setNextItemId(null);

        mockMvc.perform(patch("/shopping-items/{itemId}/position", item.getId())
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reorderItem_neighborAlreadyBought_returnsBadRequest() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem item = createItem(group, "Pane", null, true, 1000.0);
        ShoppingItem bought = createItem(group, "Latte", null, false, 2000.0);

        ReorderShoppingItemRequest request = new ReorderShoppingItemRequest();
        request.setPrevItemId(bought.getId());

        mockMvc.perform(patch("/shopping-items/{itemId}/position", item.getId())
                        .with(user(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reorderItem_asNonMember_returnsForbidden() throws Exception {
        User member = createUser("member", "member@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem item = createItem(group, "Pane", null, true, 1000.0);

        ReorderShoppingItemRequest request = new ReorderShoppingItemRequest();

        mockMvc.perform(patch("/shopping-items/{itemId}/position", item.getId())
                        .with(user(outsider))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteItem_success_returnsOk() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem item = createItem(group, "Pane", null, true, 1000.0);

        mockMvc.perform(delete("/shopping-items/{id}", item.getId())
                        .with(user(member)))
                .andExpect(status().isOk());

        assertThat(shoppingItemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    void deleteItem_asNonMember_returnsForbidden() throws Exception {
        User member = createUser("member", "member@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Casa");
        addMember(group, member, GroupRole.MEMBER);
        ShoppingItem item = createItem(group, "Pane", null, true, 1000.0);

        mockMvc.perform(delete("/shopping-items/{id}", item.getId())
                        .with(user(outsider)))
                .andExpect(status().isForbidden());

        assertThat(shoppingItemRepository.findById(item.getId())).isPresent();
    }

    @Test
    void endpoints_withoutAuthentication_returnsForbidden() throws Exception {
        mockMvc.perform(get("/shopping-items/group/{groupId}", 1L))
                .andExpect(status().isForbidden());
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("password");
        user.setRegDate(LocalDate.now());
        return userRepository.save(user);
    }

    private Group createGroup(String name) {
        Group group = new Group();
        group.setName(name);
        group.setDescription("Test group");
        group.setCreationDate(LocalDate.now());
        return groupRepository.save(group);
    }

    private void addMember(Group group, User user, GroupRole role) {
        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setDataIngresso(LocalDate.now());
        userGroup.setRole(role);
        userGroupRepository.save(userGroup);
    }

    private ShoppingItem createItem(Group group, String name, String note, boolean toBuy, double position) {
        ShoppingItem item = new ShoppingItem();
        item.setGroup(group);
        item.setName(name);
        item.setNote(note);
        item.setToBuy(toBuy);
        item.setPosition(position);
        item.setCreatedAt(LocalDateTime.now());
        return shoppingItemRepository.save(item);
    }
}
