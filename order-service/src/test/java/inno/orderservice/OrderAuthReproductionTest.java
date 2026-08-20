package inno.orderservice;

import inno.orderservice.client.UserServiceClient;
import inno.orderservice.config.WebMvcConfig;
import inno.orderservice.controller.OrderController;
import inno.orderservice.dao.repository.OrderRepository;
import inno.orderservice.dto.response.OrderResponse;
import inno.orderservice.entity.OrderStatus;
import inno.orderservice.mapper.OrderMapper;
import inno.orderservice.security.CurrentUser;
import inno.orderservice.security.IdentityRequirementInterceptor;
import inno.orderservice.security.util.ResourceSecurityService;
import inno.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({ResourceSecurityService.class, CurrentUser.class, IdentityRequirementInterceptor.class, WebMvcConfig.class})
class OrderAuthReproductionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private OrderMapper orderMapper;

    @MockBean
    private OrderRepository orderRepository;

    @Test
    void postOrdersWithoutIdentityReturns401() throws Exception {
        mockMvc.perform(post("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrdersWithoutIdentityReturns401() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrdersWithMalformedIdReturns401() throws Exception {
        mockMvc.perform(get("/orders").header(CurrentUser.USER_ID_HEADER, "not-a-uuid")
                        .header(CurrentUser.USER_ROLE_HEADER, "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getOrdersWithUserRoleReturns403() throws Exception {
        mockMvc.perform(get("/orders").header(CurrentUser.USER_ID_HEADER, UUID.randomUUID().toString())
                        .header(CurrentUser.USER_ROLE_HEADER, "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postOrdersWithValidIdentitySucceeds() throws Exception {
        when(orderService.createOrder(any())).thenReturn(new OrderResponse(
                UUID.randomUUID(), UUID.randomUUID(), "a@b.c", OrderStatus.CREATED,
                BigDecimal.ZERO, false, List.of(), null));
        mockMvc.perform(post("/orders").header(CurrentUser.USER_ID_HEADER, UUID.randomUUID().toString())
                        .header(CurrentUser.USER_ROLE_HEADER, "ADMIN")
                        .contentType("application/json")
                        .content("{\"email\":\"a@b.c\",\"items\":[{\"itemId\":\"00000000-0000-0000-0000-000000000000\",\"quantity\":1}]}"))
                .andExpect(status().isCreated());
    }
}