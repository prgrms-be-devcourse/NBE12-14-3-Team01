package com.merge.backend.domain.shift.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.merge.backend.domain.shift.entity.UnavailableTime;
import com.merge.backend.domain.shift.exception.UnavailableTimeErrorCode;
import com.merge.backend.domain.shift.service.UnavailableTimeService;
import com.merge.backend.global.dto.ConfirmationRequiredResponse;
import com.merge.backend.global.exception.BusinessException;
import com.merge.backend.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UnavailableTimeControllerTest {

    @Mock
    private UnavailableTimeService unavailableTimeService;

    @InjectMocks
    private UnavailableTimeController unavailableTimeController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(unavailableTimeController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void 근무_불가능_일정을_조회하면_200과_JSON_배열을_반환한다() throws Exception {
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 10, 10, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 10, 10, 12, 0);

        when(unavailableTimeService.findAll()).thenReturn(
            List.of(
                new UnavailableTimeService.UnavailableTimeResult(
                    10L,
                    startAt,
                    endAt,
                    true
                )
            )
        );

        mockMvc.perform(get("/unavailable-times"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.message").value("근무 불가능 일정을 조회했습니다."))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].unavailableTimeId").value(10L))
            .andExpect(jsonPath("$.data[0].startAt").value("2026-10-10T10:00:00"))
            .andExpect(jsonPath("$.data[0].endAt").value("2026-10-10T12:00:00"))
            .andExpect(jsonPath("$.data[0].officialShiftConflict").value(true));

        verify(unavailableTimeService).findAll();
    }

    @Test
    void 근무_불가능_일정을_등록하면_201과_JSON_응답을_반환한다() throws Exception {
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 10, 10, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 10, 10, 12, 0);
        UnavailableTime unavailableTime = unavailableTime(10L, startAt, endAt);

        when(unavailableTimeService.register(startAt, endAt, false))
            .thenReturn(unavailableTime);

        mockMvc.perform(
                post("/unavailable-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": "2026-10-10T10:00:00",
                          "endAt": "2026-10-10T12:00:00",
                          "confirmOfficialShiftConflict": false
                        }
                        """)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("201"))
            .andExpect(jsonPath("$.message").value("근무 불가능 일정이 등록되었습니다"))
            .andExpect(jsonPath("$.data.unavailableTimeId").value(10L))
            .andExpect(jsonPath("$.data.startAt").value("2026-10-10T10:00:00"))
            .andExpect(jsonPath("$.data.endAt").value("2026-10-10T12:00:00"));

        verify(unavailableTimeService).register(startAt, endAt, false);
    }

    @Test
    void 근무_불가능_일정을_수정하면_200과_JSON_응답을_반환한다() throws Exception {
        long unavailableTimeId = 10L;
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 11, 10, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 10, 11, 12, 0);
        UnavailableTime unavailableTime = unavailableTime(unavailableTimeId, startAt, endAt);

        when(unavailableTimeService.update(unavailableTimeId, startAt, endAt, true))
            .thenReturn(unavailableTime);

        mockMvc.perform(
                patch("/unavailable-times/{unavailableTimeId}", unavailableTimeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": "2026-10-11T10:00:00",
                          "endAt": "2026-10-11T12:00:00",
                          "confirmOfficialShiftConflict": true
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.message").value("근무 불가능 일정이 수정되었습니다"))
            .andExpect(jsonPath("$.data.unavailableTimeId").value(unavailableTimeId))
            .andExpect(jsonPath("$.data.startAt").value("2026-10-11T10:00:00"))
            .andExpect(jsonPath("$.data.endAt").value("2026-10-11T12:00:00"));

        verify(unavailableTimeService).update(unavailableTimeId, startAt, endAt, true);
    }

    @Test
    void 근무_불가능_일정을_삭제하면_200과_null_data를_반환한다() throws Exception {
        long unavailableTimeId = 10L;

        mockMvc.perform(delete("/unavailable-times/{unavailableTimeId}", unavailableTimeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("200"))
            .andExpect(jsonPath("$.message").value("근무 불가능 일정이 삭제되었습니다."))
            .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verify(unavailableTimeService).delete(unavailableTimeId);
    }

    @Test
    void 등록_요청의_startAt이_누락되면_COM_001을_반환한다() throws Exception {
        mockMvc.perform(
                post("/unavailable-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "endAt": "2026-10-10T12:00:00",
                          "confirmOfficialShiftConflict": false
                        }
                        """)
            )
            .andExpect(validationError("startAt", "시작 시간은 필수로 입력하여야 합니다."));

        verifyNoInteractions(unavailableTimeService);
    }

    @Test
    void 등록_요청의_endAt이_null이면_COM_001을_반환한다() throws Exception {
        mockMvc.perform(
                post("/unavailable-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": "2026-10-10T10:00:00",
                          "endAt": null,
                          "confirmOfficialShiftConflict": false
                        }
                        """)
            )
            .andExpect(validationError("endAt", "끝나는 시간은 필수로 입력하여야 합니다."));

        verifyNoInteractions(unavailableTimeService);
    }

    @Test
    void 등록_요청의_공식_Shift_확인값이_null이면_COM_001을_반환한다() throws Exception {
        mockMvc.perform(
                post("/unavailable-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": "2026-10-10T10:00:00",
                          "endAt": "2026-10-10T12:00:00",
                          "confirmOfficialShiftConflict": null
                        }
                        """)
            )
            .andExpect(validationError("confirmOfficialShiftConflict", "must not be null"));

        verifyNoInteractions(unavailableTimeService);
    }

    @Test
    void 수정_요청의_startAt이_null이면_COM_001을_반환한다() throws Exception {
        mockMvc.perform(
                patch("/unavailable-times/{unavailableTimeId}", 10L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": null,
                          "endAt": "2026-10-11T12:00:00",
                          "confirmOfficialShiftConflict": false
                        }
                        """)
            )
            .andExpect(validationError("startAt", "시작시간은 필수로 입력하셔야 합니다."));

        verifyNoInteractions(unavailableTimeService);
    }

    @Test
    void 수정_요청의_endAt이_누락되면_COM_001을_반환한다() throws Exception {
        mockMvc.perform(
                patch("/unavailable-times/{unavailableTimeId}", 10L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": "2026-10-11T10:00:00",
                          "confirmOfficialShiftConflict": false
                        }
                        """)
            )
            .andExpect(validationError("endAt", "끝나는 시간은 필수로 입력하셔야 합니다."));

        verifyNoInteractions(unavailableTimeService);
    }

    @Test
    void 수정_요청의_공식_Shift_확인값이_누락되면_COM_001을_반환한다() throws Exception {
        mockMvc.perform(
                patch("/unavailable-times/{unavailableTimeId}", 10L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": "2026-10-11T10:00:00",
                          "endAt": "2026-10-11T12:00:00"
                        }
                        """)
            )
            .andExpect(validationError("confirmOfficialShiftConflict", "must not be null"));

        verifyNoInteractions(unavailableTimeService);
    }

    @Test
    void 등록_요청의_날짜_형식이_잘못되면_COM_002를_반환한다() throws Exception {
        mockMvc.perform(
                post("/unavailable-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": "not-a-date-time",
                          "endAt": "2026-10-10T12:00:00",
                          "confirmOfficialShiftConflict": false
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-002"))
            .andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않습니다."))
            .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verifyNoInteractions(unavailableTimeService);
    }

    @Test
    void 숫자가_아닌_일정_ID로_수정하면_COM_003을_반환한다() throws Exception {
        mockMvc.perform(
                patch("/unavailable-times/{unavailableTimeId}", "not-a-number")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("COM-003"))
            .andExpect(jsonPath("$.message").value("요청 파라미터가 올바르지 않습니다."))
            .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verifyNoInteractions(unavailableTimeService);
    }

    @Test
    void 등록_중_공식_Shift와_충돌하면_확인_요청_응답을_반환한다() throws Exception {
        LocalDateTime startAt = LocalDateTime.of(2026, 10, 10, 10, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 10, 10, 12, 0);

        when(unavailableTimeService.register(startAt, endAt, false)).thenThrow(
            new BusinessException(
                UnavailableTimeErrorCode.OFFICIAL_SHIFT_CONFLICT,
                new ConfirmationRequiredResponse(true)
            )
        );

        mockMvc.perform(
                post("/unavailable-times")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "startAt": "2026-10-10T10:00:00",
                          "endAt": "2026-10-10T12:00:00",
                          "confirmOfficialShiftConflict": false
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("OFFICIAL_SHIFT_CONFLICT"))
            .andExpect(jsonPath("$.message").value("이미 확정된 근무와 겹치는 시간입니다."))
            .andExpect(jsonPath("$.data.requiresConfirmation").value(true));

        verify(unavailableTimeService).register(startAt, endAt, false);
    }

    private org.springframework.test.web.servlet.ResultMatcher validationError(
        String field,
        String message
    ) {
        return result -> {
            status().isBadRequest().match(result);
            jsonPath("$.success").value(false).match(result);
            jsonPath("$.code").value("COM-001").match(result);
            jsonPath("$.message").value("요청 값이 올바르지 않습니다.").match(result);
            jsonPath("$.data.fieldErrors[0].field").value(field).match(result);
            jsonPath("$.data.fieldErrors[0].message").value(message).match(result);
        };
    }

    private UnavailableTime unavailableTime(
        long id,
        LocalDateTime startAt,
        LocalDateTime endAt
    ) {
        UnavailableTime unavailableTime = org.mockito.Mockito.mock(UnavailableTime.class);

        when(unavailableTime.getId()).thenReturn(id);
        when(unavailableTime.getStartAt()).thenReturn(startAt);
        when(unavailableTime.getEndAt()).thenReturn(endAt);

        return unavailableTime;
    }
}
