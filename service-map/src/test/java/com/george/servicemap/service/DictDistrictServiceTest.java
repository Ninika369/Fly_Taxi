package com.george.servicemap.service;

import com.george.internalCommon.constant.CommonStatus;
import com.george.internalCommon.dto.DicDistrict;
import com.george.internalCommon.dto.ResponseResult;
import com.george.servicemap.mapper.DicDistrictMapper;
import com.george.servicemap.remote.MapDicDistrictClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictDistrictServiceTest {

    @Mock
    private MapDicDistrictClient mapDicDistrictClient;

    @Mock
    private DicDistrictMapper dicDistrictMapper;

    @InjectMocks
    private DictDistrictService dictDistrictService;

    @Test
    @DisplayName("Amap district status failure returns without writing districts")
    void shouldReturnMapDistrictErrorAndSkipInsert_whenProviderStatusFails() {
        when(mapDicDistrictClient.dicDistrict("New Zealand"))
                .thenReturn("{\"status\":0,\"info\":\"INVALID_USER_KEY\"}");

        ResponseResult result = dictDistrictService.initDicDistrict("New Zealand");

        assertEquals(CommonStatus.MAP_DISTRICT_ERROR.getCode(), result.getCode());
        assertEquals(CommonStatus.MAP_DISTRICT_ERROR.getMessage(), result.getMessage());
        verify(dicDistrictMapper, never()).insert((DicDistrict) org.mockito.ArgumentMatchers.any());
    }
}
