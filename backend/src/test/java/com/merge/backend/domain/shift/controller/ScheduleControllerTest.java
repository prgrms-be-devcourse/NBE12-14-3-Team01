package com.merge.backend.domain.shift.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.merge.backend.domain.shift.dto.ManagerScheduleResponse;
import com.merge.backend.domain.shift.dto.ScheduleCreateRequest;
import com.merge.backend.domain.shift.dto.ScheduleCreateResponse;
import com.merge.backend.domain.shift.dto.SchedulePublishRequest;
import com.merge.backend.domain.shift.dto.SchedulePublishResponse;
import com.merge.backend.domain.shift.entity.ScheduleStatus;
import com.merge.backend.domain.shift.service.ScheduleService;
import com.merge.backend.global.dto.ApiResponse;
import com.merge.backend.global.exception.GlobalExceptionHandler;
import com.merge.backend.global.rq.Rq;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;


@ExtendWith(MockitoExtension.class)
class ScheduleControllerTest {

    @Mock
    private ScheduleService scheduleService;

    @Mock
    private Rq rq;

    @InjectMocks
    private ScheduleController scheduleController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(scheduleController)
                .build();
    }

    @Test
    void Schedule이_없으면_200과_data_null을_반환한다() {

        // given
        Long actorUserId = 1L;
        Long workplaceId = 10L;

        LocalDate weekStartDate =
            LocalDate.of(2026, 9, 21);

        when(rq.getActorId())
            .thenReturn(actorUserId);

        when(
            scheduleService.getWeeklySchedule(
                actorUserId,
                workplaceId,
                weekStartDate
            )
        ).thenReturn(null);

        // when
        ResponseEntity<ApiResponse<ManagerScheduleResponse>> result =
            scheduleController.getWeeklySchedule(
                workplaceId,
                weekStartDate
            );

        // then
        assertThat(result.getStatusCode())
            .isEqualTo(HttpStatus.OK);

        assertThat(result.getBody())
            .isNotNull();

        assertThat(result.getBody().isSuccess())
            .isTrue();

        assertThat(result.getBody().getCode())
            .isEqualTo("200");

        assertThat(result.getBody().getMessage())
            .isEqualTo(
                "해당 주차의 근무표가 아직 생성되지 않았습니다."
            );

        assertThat(result.getBody().getData())
            .isNull();

        verify(rq)
            .getActorId();

        verify(scheduleService)
            .getWeeklySchedule(
                actorUserId,
                workplaceId,
                weekStartDate
            );
    }

    @Test
    void 유효한_요청이면_주간_DRAFT_Schedule을_생성하고_201을_반환한다() {

        // given
        Long actorUserId = 1L;
        Long workplaceId = 10L;

        LocalDate weekStartDate =
            LocalDate.of(2026, 9, 21);

        ScheduleCreateRequest request =
            new ScheduleCreateRequest(
                weekStartDate
            );

        ScheduleCreateResponse serviceResponse =
            new ScheduleCreateResponse(
                20L,
                workplaceId,
                weekStartDate,
                ScheduleStatus.DRAFT,
                3
            );

        when(rq.getActorId())
            .thenReturn(actorUserId);

        when(
            scheduleService.createDraftSchedule(
                actorUserId,
                workplaceId,
                weekStartDate
            )
        ).thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<ScheduleCreateResponse>> result =
            scheduleController.createDraftSchedule(
                workplaceId,
                request
            );

        // then
        assertThat(result.getStatusCode())
            .isEqualTo(HttpStatus.CREATED);

        assertThat(result.getBody())
            .isNotNull();

        assertThat(result.getBody().isSuccess())
            .isTrue();

        assertThat(result.getBody().getCode())
            .isEqualTo("201");

        assertThat(result.getBody().getMessage())
            .isEqualTo("주간 근무표 초안이 생성되었습니다.");

        assertThat(result.getBody().getData())
            .isEqualTo(serviceResponse);

        verify(rq)
            .getActorId();

        verify(scheduleService)
            .createDraftSchedule(
                actorUserId,
                workplaceId,
                weekStartDate
            );
    }

    @Test
    void Schedule이_존재하면_200과_주간_근무표를_반환한다() {

        // given
        Long actorUserId = 1L;
        Long workplaceId = 10L;

        LocalDate weekStartDate =
            LocalDate.of(2026, 9, 21);

        ManagerScheduleResponse serviceResponse =
            new ManagerScheduleResponse(
                20L,
                workplaceId,
                weekStartDate,
                ScheduleStatus.DRAFT,
                null,
                List.of()
            );

        when(rq.getActorId())
            .thenReturn(actorUserId);

        when(
            scheduleService.getWeeklySchedule(
                actorUserId,
                workplaceId,
                weekStartDate
            )
        ).thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<ManagerScheduleResponse>> result =
            scheduleController.getWeeklySchedule(
                workplaceId,
                weekStartDate
            );

        // then
        assertThat(result.getStatusCode())
            .isEqualTo(HttpStatus.OK);

        assertThat(result.getBody())
            .isNotNull();

        assertThat(result.getBody().isSuccess())
            .isTrue();

        assertThat(result.getBody().getCode())
            .isEqualTo("200");

        assertThat(result.getBody().getMessage())
            .isEqualTo(
                "주간 근무표를 조회했습니다."
            );

        assertThat(result.getBody().getData())
            .isEqualTo(serviceResponse);

        verify(rq)
            .getActorId();

        verify(scheduleService)
            .getWeeklySchedule(
                actorUserId,
                workplaceId,
                weekStartDate
            );
    }

    @Test
    void 유효한_공개_요청이면_Schedule을_공개하고_200을_반환한다() {

        // given
        Long actorUserId = 1L;
        Long workplaceId = 10L;
        Long scheduleId = 20L;

        LocalDate weekStartDate =
            LocalDate.of(
                2026, 9, 21
            );

        LocalDateTime publishedAt =
            LocalDateTime.of(
                2026, 9, 19,
                18, 30
            );

        SchedulePublishRequest request =
            new SchedulePublishRequest(
                false
            );

        SchedulePublishResponse serviceResponse =
            new SchedulePublishResponse(
                scheduleId,
                workplaceId,
                weekStartDate,
                ScheduleStatus.PUBLISHED,
                publishedAt
            );

        when(rq.getActorId())
            .thenReturn(actorUserId);

        when(
            scheduleService.publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false
            )
        ).thenReturn(serviceResponse);

        // when
        ResponseEntity<ApiResponse<SchedulePublishResponse>> result =
            scheduleController.publishSchedule(
                workplaceId,
                scheduleId,
                request
            );

        // then
        assertThat(result.getStatusCode())
            .isEqualTo(HttpStatus.OK);

        assertThat(result.getBody())
            .isNotNull();

        assertThat(result.getBody().isSuccess())
            .isTrue();

        assertThat(result.getBody().getCode())
            .isEqualTo("200");

        assertThat(result.getBody().getMessage())
            .isEqualTo(
                "근무표가 공개되었습니다."
            );

        assertThat(result.getBody().getData())
            .isEqualTo(serviceResponse);

        verify(rq)
            .getActorId();

        verify(scheduleService)
            .publishSchedule(
                actorUserId,
                workplaceId,
                scheduleId,
                false
            );
    }

    @Test
    void 공개_확인값이_누락되면_400을_반환한다() throws Exception {

        MockMvc validationMockMvc =
            MockMvcBuilders
                .standaloneSetup(scheduleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // when & then
        validationMockMvc.perform(
                patch(
                    "/workplaces/{workplaceId}/schedules/{scheduleId}/publish",
                    10L,
                    20L
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
            .andExpect(
                jsonPath("$.data.fieldErrors[0].field")
                    .value("confirmUnavailableConflict")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].message")
                    .value("근무 불가능 일정 충돌 확인 여부는 필수입니다.")
            );

        verifyNoInteractions(
            rq,
            scheduleService
        );
    }

    @Test
    void 주_시작일이_누락되면_COM_001_필드_오류를_반환한다() throws Exception {

        MockMvc validationMockMvc =
            MockMvcBuilders
                .standaloneSetup(scheduleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        validationMockMvc.perform(
                post("/workplaces/{workplaceId}/schedules", 10L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COM-001"))
            .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("weekStartDate"))
            .andExpect(
                jsonPath("$.data.fieldErrors[0].message")
                    .value("주 시작일은 필수입니다.")
            );

        verifyNoInteractions(
            rq,
            scheduleService
        );
    }

}
