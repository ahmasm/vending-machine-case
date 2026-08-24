package io.github.ahmasm.vending.machine.application.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "spring.flyway.locations=classpath:db/migration,classpath:db/demo")
@AutoConfigureMockMvc
@Testcontainers
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProductAvailabilityIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final PostgreSQLContainer<?> postgres = POSTGRES;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    ProductAvailabilityIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void listsProductsInSlotOrderWithCurrentAvailability() throws Exception {
        insertMachine("VM-PRODUCTS");
        insertSlot("VM-PRODUCTS", "B2", "WATER", "Water", 15, 0);
        insertSlot("VM-PRODUCTS", "A1", "COKE", "Coke", 35, 3);

        mockMvc.perform(get("/api/v1/machines/{machineId}/products", "VM-PRODUCTS"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.machineId").value("VM-PRODUCTS"))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].slotCode").value("A1"))
                .andExpect(jsonPath("$.products[0].product.id").value("COKE"))
                .andExpect(jsonPath("$.products[0].product.name").value("Coke"))
                .andExpect(jsonPath("$.products[0].price.amount").value(35))
                .andExpect(jsonPath("$.products[0].price.currency").value("UNIT"))
                .andExpect(jsonPath("$.products[0].availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.products[1].slotCode").value("B2"))
                .andExpect(jsonPath("$.products[1].availability").value("OUT_OF_STOCK"));
    }

    @Test
    void missingMachineReturnsNotFoundProblem() throws Exception {
        mockMvc.perform(get("/api/v1/machines/{machineId}/products", "VM-MISSING"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("MACHINE_NOT_FOUND"))
                .andExpect(jsonPath("$.instance")
                        .value("/api/v1/machines/VM-MISSING/products"));
    }

    @Test
    void demoProvisioningContainsTheTenProductsSuppliedByTheCase() {
        var products = jdbcTemplate.query(
                "select product_name, price_amount from machine_slot where machine_id = 'VM-001'",
                resultSet -> {
                    var found = new HashMap<String, Long>();
                    while (resultSet.next()) {
                        found.put(resultSet.getString("product_name"), resultSet.getLong("price_amount"));
                    }
                    return found;
                });

        assertEquals(
                Map.of(
                        "Water", 25L,
                        "Coke", 35L,
                        "Soda", 45L,
                        "Snickers", 50L,
                        "Chips", 40L,
                        "Candy Bar", 30L,
                        "Energy Drink", 60L,
                        "Juice Box", 55L,
                        "Protein Bar", 45L,
                        "Gum", 20L),
                products);
    }

    private void insertMachine(String machineId) {
        jdbcTemplate.update("insert into vending_machine (machine_id) values (?)", machineId);
    }

    private void insertSlot(
            String machineId,
            String slotCode,
            String productId,
            String productName,
            long price,
            int quantity) {
        jdbcTemplate.update(
                """
                insert into machine_slot (
                    machine_id,
                    slot_code,
                    product_id,
                    product_name,
                    price_amount,
                    price_currency,
                    quantity
                ) values (?, ?, ?, ?, ?, 'UNIT', ?)
                """,
                machineId,
                slotCode,
                productId,
                productName,
                price,
                quantity);
    }
}
