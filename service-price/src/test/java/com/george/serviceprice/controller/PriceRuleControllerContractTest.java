package com.george.serviceprice.controller;

import com.george.internalCommon.dto.ResponseResult;
import com.george.internalCommon.dto.PriceRule;
import com.george.serviceprice.service.PriceRuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PriceRuleControllerContractTest {

    @Mock
    private PriceRuleService priceRuleService;

    @InjectMocks
    private PriceRuleController priceRuleController;

    @Test
    @DisplayName("POST /price-rule/is-latest accepts JSON and returns the response schema")
    void shouldAcceptPostJsonAndReturnResponseSchema_whenCheckingLatestPriceRule() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(priceRuleController).build();
        when(priceRuleService.isLatest("110000$1", 3)).thenReturn(ResponseResult.success(true));

        mockMvc.perform(post("/price-rule/is-latest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fareType\":\"110000$1\",\"fareVersion\":3}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value(true));

        verify(priceRuleService, only()).isLatest("110000$1", 3);
    }

    @Test
    @DisplayName("POST /price-rule/if-exists accepts JSON while GET is rejected")
    void shouldAcceptOnlyPostJsonAndReturnResponseSchema_whenCheckingPriceRuleExists() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(priceRuleController).build();
        when(priceRuleService.ifExists(any(PriceRule.class))).thenReturn(ResponseResult.success(true));

        mockMvc.perform(post("/price-rule/if-exists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cityCode\":\"110000\",\"vehicleType\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data").value(true));

        ArgumentCaptor<PriceRule> priceRuleCaptor = ArgumentCaptor.forClass(PriceRule.class);
        verify(priceRuleService, times(1)).ifExists(priceRuleCaptor.capture());
        assertEquals("110000", priceRuleCaptor.getValue().getCityCode());
        assertEquals("1", priceRuleCaptor.getValue().getVehicleType());

        mockMvc.perform(get("/price-rule/if-exists"))
                .andExpect(status().isMethodNotAllowed());

        verifyNoMoreInteractions(priceRuleService);
    }
}
