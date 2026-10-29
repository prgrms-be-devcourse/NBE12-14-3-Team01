"use client";

import { useEffect, useState } from "react";
import {useParams} from "next/navigation";
import Card from "@/components/ui/Card";
import PageHeader from "@/components/ui/PageHeader";
import { apiFetch, ApiError } from "@/lib/api";

type ScheduleStatus = "NONE" | "DRAFT" | "PUBLISHED";

type ScheduleLoadStatus =
  "idle"
  | "loading"
  | "success"
  | "error";

type DayKey =
    | "mon"
    | "tue"
    | "wed"
    | "thu"
    | "fri"
    | "sat"
    | "sun";

type Shift = {
  id: number;
  memberId: number;
  memberName: string;
  day: DayKey;
  startTime: string;
  endTime: string;
  status: "SCHEDULED" | "CANCELLED";
};

type UnavailableOverlap = {
  startAt: string;
  endAt: string;
};

type UnavailableConflict = {
  shiftId: number;
  shiftStartAt: string;
  shiftEndAt: string;
  targetMemberId: number;
  targetName: string;
  overlaps: UnavailableOverlap[];
};

type NewShiftKey = {
  scheduleId: number;
  targetMemberId: number;
  shiftStartAt: string;
  shiftEndAt: string;
};

type NewShiftUnavailableConflict = {
  newShiftKey: NewShiftKey;
  targetName: string;
  overlaps: UnavailableOverlap[];
};

type PublishedReassignResponse = {
  shift: {
    shiftId: number;
    memberId: number;
    memberName: string;
    startAt: string;
    endAt: string;
    status: "SCHEDULED";
  };
  closedSubstituteRequests: number;
};

type ScheduleShiftResponse = {
  shiftId: number;
  memberId: number;
  memberName: string;
  role: "MANAGER" | "EMPLOYEE";
  startAt: string;
  endAt: string;
  status: "SCHEDULED" | "CANCELLED";
};

type ManagerScheduleResponse = {
  scheduleId: number;
  workplaceId: number;
  weekStartDate: string;
  status: "DRAFT" | "PUBLISHED";
  publishedAt: string | null;
  shifts: ScheduleShiftResponse[];
};

type WorkplaceMemberResponse = {
  memberId: number;
  name: string;
  email: string;
  role: "MANAGER" | "EMPLOYEE";
};

type ScheduleMember = {
  id: number;
  name: string;
};

type MemberLoadStatus =
  | "loading"
  | "success"
  | "error";

type PositionedShift = Shift & {
  lane: number;
  laneCount: number;
};

const DAY_KEYS: DayKey[] = [
  "mon",
  "tue",
  "wed",
  "thu",
  "fri",
  "sat",
  "sun",
];

const DAY_LABELS = [
  "월",
  "화",
  "수",
  "목",
  "금",
  "토",
  "일",
];

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

function nowInKst(): Date {
  return new Date(Date.now() + KST_OFFSET_MS);
}

function getThisMondayDateString(): string {
  const today = nowInKst();

  const day = today.getUTCDay();

  const diffToMonday =
    day === 0 ? -6 : 1 - day;

  const monday = new Date(today);

  monday.setUTCDate(
    today.getUTCDate() + diffToMonday
  );

  const year = monday.getUTCFullYear();

  const month = String(
    monday.getUTCMonth() + 1
  ).padStart(2, "0");

  const date = String(
    monday.getUTCDate()
  ).padStart(2, "0");

  return `${year}-${month}-${date}`;
}

function addDays(dateString: string, amount: number) {
  const date = new Date(`${dateString}T00:00:00Z`);

  date.setUTCDate(date.getUTCDate() + amount);

  return date.toISOString().slice(0, 10);
}

function createWeekDays(weekStartDate: string) {
  return DAY_KEYS.map((key, index) => {
    const date = addDays(weekStartDate, index);

    const [, month, day] = date.split("-");

    return {
      key,
      label: `${Number(month)}/${Number(day)} ${DAY_LABELS[index]}`,
      date,
    };
  });
}

function convertScheduleShiftToShift(
  shift: ScheduleShiftResponse
): Shift {
  const startDate = shift.startAt.slice(0, 10);

  const dayOfWeek =
    new Date(`${startDate}T00:00:00Z`).getUTCDay();

  const dayMap: Record<number, DayKey> = {
    0: "sun",
    1: "mon",
    2: "tue",
    3: "wed",
    4: "thu",
    5: "fri",
    6: "sat",
  };

  return {
    id: shift.shiftId,
    memberId: shift.memberId,
    memberName: shift.memberName,
    day: dayMap[dayOfWeek],
    startTime: shift.startAt.slice(11, 16),
    endTime: shift.endAt.slice(11, 16),
    status: shift.status,
  };
}

const START_HOUR = 9;
const END_HOUR = 22;
const HOUR_HEIGHT = 56;

const timeLabels = Array.from(
    { length: END_HOUR - START_HOUR + 1 },
    (_, index) => {
      const hour = START_HOUR + index;
      return `${String(hour).padStart(2, "0")}:00`;
    }
);

function timeToMinutes(time: string) {
  const [hour, minute] = time.split(":").map(Number);
  return hour * 60 + minute;
}

// 같은 시간대에 겹치는 근무를 좌우로 나누기 위한 함수
function getPositionedShifts(shifts: Shift[]): PositionedShift[] {
  const sorted = [...shifts].sort(
      (a, b) => timeToMinutes(a.startTime) - timeToMinutes(b.startTime)
  );

  const result: PositionedShift[] = [];

  let currentGroup: Shift[] = [];
  let groupEnd = -1;

  const processGroup = (group: Shift[]) => {
    if (group.length === 0) {
      return;
    }

    const laneEnds: number[] = [];
    const positioned: { shift: Shift; lane: number }[] = [];

    group.forEach((shift) => {
      const start = timeToMinutes(shift.startTime);
      const end = timeToMinutes(shift.endTime);

      let lane = laneEnds.findIndex((laneEnd) => laneEnd <= start);

      if (lane === -1) {
        lane = laneEnds.length;
        laneEnds.push(end);
      } else {
        laneEnds[lane] = end;
      }

      positioned.push({ shift, lane });
    });

    const laneCount = laneEnds.length;

    positioned.forEach(({ shift, lane }) => {
      result.push({
        ...shift,
        lane,
        laneCount,
      });
    });
  };

  sorted.forEach((shift) => {
    const start = timeToMinutes(shift.startTime);
    const end = timeToMinutes(shift.endTime);

    if (currentGroup.length > 0 && start >= groupEnd) {
      processGroup(currentGroup);
      currentGroup = [];
      groupEnd = -1;
    }

    currentGroup.push(shift);
    groupEnd = Math.max(groupEnd, end);
  });

  processGroup(currentGroup);

  return result;
}

function getShiftStyle(shift: PositionedShift) {
  const startMinutes = timeToMinutes(shift.startTime);
  const endMinutes = timeToMinutes(shift.endTime);
  const scheduleStartMinutes = START_HOUR * 60;

  const top =
      ((startMinutes - scheduleStartMinutes) / 60) * HOUR_HEIGHT;

  const height = ((endMinutes - startMinutes) / 60) * HOUR_HEIGHT;

  return {
    top: `${top}px`,
    height: `${height}px`,
    left: `calc(${(shift.lane / shift.laneCount) * 100}% + 4px)`,
    width: `calc(${100 / shift.laneCount}% - 8px)`,
  };
}

// 에러 코드 상수 정의
const UNAVAILABLE_TIME_CONFLICT_CODE = "SFT-015";
const PUBLISHED_UNAVAILABLE_CONFIRMATION_CODE = "SFT-021";
const PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED_CODE = "SFT-020";
const SHIFT_OVERLAP_CODES = ["SFT-009", "SFT-010"];
const SAME_SHIFT_MEMBER_REASSIGNMENT_CODE = "SFT-022";

// API 요청 함수 정의
async function getManagerSchedule(
  workplaceId: string,
  weekStartDate: string
) {
  return await apiFetch<ManagerScheduleResponse | null>(
    `/workplaces/${workplaceId}/schedules?weekStartDate=${weekStartDate}`
  );
}

async function createSchedule(
  workplaceId: string,
  weekStartDate: string
) {
  return await apiFetch<{
    scheduleId: number;
    workplaceId: number;
    weekStartDate: string;
    status: "DRAFT";
    shiftCount: number;
  }>(
    `/workplaces/${workplaceId}/schedules`,
    {
      method: "POST",
      body: JSON.stringify({
        weekStartDate,
      }),
    }
  );
}

async function createShift(
    workplaceId: string,
    scheduleId: number,
    payload: {
      memberId: number;
      startAt: string;
      endAt: string;
      confirmUnavailableConflict: boolean;
    }
) {
  return await apiFetch<{ shiftId: number; memberName: string; }>(
      `/workplaces/${workplaceId}/schedules/${scheduleId}/shifts`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

// 근무 수정 API (PATCH)
async function updateShift(
    workplaceId: string,
    scheduleId: number,
    shiftId: number,
    payload: {
      memberId: number;
      startAt: string;
      endAt: string;
      confirmUnavailableConflict: boolean;
    }
) {
  return await apiFetch<{ shiftId: number; memberName: string }>(
      `/workplaces/${workplaceId}/schedules/${scheduleId}/shifts/${shiftId}`,
      {
        method: "PATCH",
        body: JSON.stringify(payload),
      }
  );
}

async function publishSchedule(
  workplaceId: string,
  scheduleId: number,
  confirmUnavailableConflict: boolean
) {
  return await apiFetch<{
    scheduleId: number;
    workplaceId: number;
    weekStartDate: string;
    status: "PUBLISHED";
    publishedAt: string;
  }>(
    `/workplaces/${workplaceId}/schedules/${scheduleId}/publish`,
    {
      method: "PATCH",
      body: JSON.stringify({
        confirmUnavailableConflict,
      }),
    }
  );
}

// 근무 삭제 API (DELETE)
async function deleteShift(
    workplaceId: string,
    scheduleId: number,
    shiftId: number
) {
  return await apiFetch(
      `/workplaces/${workplaceId}/schedules/${scheduleId}/shifts/${shiftId}`,
      {
        method: "DELETE",
      }
  );
}

async function cancelPublishedShift(
    workplaceId: string,
    scheduleId: number,
    shiftId: number
) {
  return await apiFetch<{
    shift: { shiftId: number; status: "CANCELLED" };
    closedSubstituteRequests: number;
  }>(
      `/workplaces/${workplaceId}/schedules/${scheduleId}/shifts/${shiftId}/cancel`,
      { method: "POST" }
  );
}

async function reassignPublishedShift(
    workplaceId: string,
    scheduleId: number,
    shiftId: number,
    targetMemberId: number,
    acknowledgedUnavailableConflicts: Array<{
      shiftId: number;
      targetMemberId: number;
      overlaps: UnavailableOverlap[];
    }>
) {
  return await apiFetch<PublishedReassignResponse>(
      `/workplaces/${workplaceId}/schedules/${scheduleId}/shifts/${shiftId}/reassign`,
      {
        method: "POST",
        body: JSON.stringify({
          targetMemberId,
          acknowledgedUnavailableConflicts,
        }),
      }
  );
}

async function createPublishedShift(
    workplaceId: string,
    scheduleId: number,
    payload: {
      memberId: number;
      startAt: string;
      endAt: string;
      acknowledgedUnavailableConflicts: Array<{
        newShiftKey: NewShiftKey;
        overlaps: UnavailableOverlap[];
      }>;
    }
) {
  return await apiFetch<{
    shiftId: number;
    memberId: number;
    memberName: string;
    startAt: string;
    endAt: string;
    status: "SCHEDULED";
  }>(
      `/workplaces/${workplaceId}/schedules/${scheduleId}/published-shifts`,
      {
        method: "POST",
        body: JSON.stringify(payload),
      }
  );
}

export default function SchedulePage() {
  const params = useParams<{ workplaceId: string }>();
  const workplaceId = params.workplaceId;
  const [weekStartDate, setWeekStartDate] = useState(getThisMondayDateString);
  const days = createWeekDays(weekStartDate);
  const [schedule, setSchedule] = useState<ManagerScheduleResponse | null>(null);
  const [scheduleLoadStatus, setScheduleLoadStatus] = useState<ScheduleLoadStatus>("idle");
  const [scheduleReloadKey, setScheduleReloadKey] = useState(0);
  const [loadedScheduleKey, setLoadedScheduleKey] = useState<string | null>(null);
  const [status, setStatus] = useState<ScheduleStatus>("NONE");
  const [shifts, setShifts] = useState<Shift[]>([]);

  const [scheduleId, setScheduleId] = useState<number | null>(null);
  const [showShiftModal, setShowShiftModal] = useState(false);
  const [showPublishedCreateShiftModal, setShowPublishedCreateShiftModal] = useState(false);
  const [showPublishedShiftModal, setShowPublishedShiftModal] = useState(false);
  const [selectedPublishedShift, setSelectedPublishedShift] = useState<Shift | null>(null);
  const [selectedReassignMemberId, setSelectedReassignMemberId] = useState<number | null>(null);
  const [publishedShiftError, setPublishedShiftError] = useState("");
  const [unavailableConflicts, setUnavailableConflicts] = useState<UnavailableConflict[]>([]);
  const [publishedCreateMemberId, setPublishedCreateMemberId] = useState<number | null>(null);
  const [publishedCreateDay, setPublishedCreateDay] = useState<DayKey>("mon");
  const [publishedCreateStartTime, setPublishedCreateStartTime] = useState("09:00");
  const [publishedCreateEndTime, setPublishedCreateEndTime] = useState("18:00");
  const [publishedCreateError, setPublishedCreateError] = useState("");
  const [publishedCreateConflicts, setPublishedCreateConflicts] = useState<NewShiftUnavailableConflict[]>([]);
  const [members, setMembers] = useState<ScheduleMember[]>([]);
  const [memberLoadStatus, setMemberLoadStatus] = useState<MemberLoadStatus>("loading");
  const [loadedMembersWorkplaceId, setLoadedMembersWorkplaceId] = useState<string | null>(null);
  const [memberReloadKey, setMemberReloadKey] = useState(0);
  const [editingShiftId, setEditingShiftId] = useState<number | null>(null);
  const [selectedMemberId, setSelectedMemberId] = useState<number | null>(null);
  const [selectedDay, setSelectedDay] = useState<DayKey>("mon");
  const [startTime, setStartTime] = useState("09:00");
  const [endTime, setEndTime] = useState("18:00");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const currentScheduleKey = `${workplaceId}:${weekStartDate}:${scheduleReloadKey}`;

  const isCurrentScheduleReady =
    scheduleLoadStatus === "success" &&
    loadedScheduleKey === currentScheduleKey;

  const areCurrentMembersReady = memberLoadStatus === "success" && loadedMembersWorkplaceId === workplaceId;

  useEffect(() => {
    let cancelled = false;

    const loadMembers = async () => {
      setMemberLoadStatus("loading");
      setLoadedMembersWorkplaceId(null);

      try {
        const result =
          await apiFetch<WorkplaceMemberResponse[]>(
            `/workplaces/${workplaceId}/members`
          );

        if (cancelled) {
          return;
        }

        const loadedMembers: ScheduleMember[] =
          result.map((member) => ({
            id: member.memberId,
            name: member.name,
          }));

        setMembers(loadedMembers);

        setSelectedMemberId((prev) => {
          if (
            prev !== null &&
            loadedMembers.some((member) => member.id === prev)
          ) {
            return prev;
          }

          return loadedMembers[0]?.id ?? null;
        });

        setLoadedMembersWorkplaceId(workplaceId);
        setMemberLoadStatus("success");
      } catch {
        if (cancelled) {
          return;
        }

        setMembers([]);
        setSelectedMemberId(null);
        setLoadedMembersWorkplaceId(workplaceId);
        setMemberLoadStatus("error");
      }
    };

    void loadMembers();

    return () => {
      cancelled = true;
    };
  }, [workplaceId, memberReloadKey]);

  useEffect(() => {
    let ignore = false;

    const requestKey = `${workplaceId}:${weekStartDate}:${scheduleReloadKey}`;

    const loadSchedule = async () => {
      setScheduleLoadStatus("loading");

      try {
        const result = await getManagerSchedule(
          workplaceId,
          weekStartDate
        );

        if (ignore) {
          return;
        }

        setLoadedScheduleKey(requestKey);
        setSchedule(result);

        if (result === null) {
          setStatus("NONE");
          setScheduleId(null);
          setShifts([]);
        } else {
          setStatus(result.status);
          setScheduleId(result.scheduleId);
          setShifts(
            result.shifts.map(convertScheduleShiftToShift)
          );
        }

        setScheduleLoadStatus("success");
      } catch {
        if (ignore) {
          return;
        }

        setLoadedScheduleKey(null);
        setSchedule(null);
        setScheduleLoadStatus("error");
      }
    };

    void loadSchedule();

    return () => {
      ignore = true;
    };
  }, [workplaceId, weekStartDate, scheduleReloadKey]);

  const handlePreviousWeek = () => {
    if (isSubmitting || !isCurrentScheduleReady) {
      return;
    }

    setWeekStartDate((prev) => addDays(prev, -7));
  };

  const handleNextWeek = () => {
    if (isSubmitting || !isCurrentScheduleReady) {
      return;
    }

    setWeekStartDate((prev) => addDays(prev, 7));
  };

  const handleRetrySchedule = () => {
    if (isSubmitting) {
      return;
    }

    setScheduleReloadKey((prev) => prev + 1);
  };

  const handleRetryMembers = () => {
    if (isSubmitting) {
      return;
    }

    setMemberReloadKey((prev) => prev + 1);
  };

  const handleCreateSchedule = async () => {
    // 이미 다른 생성/공개/Shift 작업이 진행 중이면 중복 실행 방지
    if (isSubmitting) {
      return;
    }

    // 현재 화면의 주차가 최신 조회 결과인지,
    // 그리고 실제로 근무표가 없는 상태인지 확인
    if (!isCurrentScheduleReady || status !== "NONE") {
      window.alert(
        "현재 주차의 근무표 상태를 확인 중입니다. 잠시 후 다시 시도해주세요."
      );
      return;
    }

    // 여기서부터 근무표 생성 작업 잠금
    setIsSubmitting(true);

    try {
      await createSchedule(
        workplaceId,
        weekStartDate
      );

      // 생성 성공 후 서버의 최신 DRAFT 상태를 다시 조회
      setScheduleReloadKey((prev) => prev + 1);
    } catch (err) {
      if (err instanceof ApiError) {
        window.alert(err.message);
        return;
      }

      window.alert(
        "근무표 생성 중 오류가 발생했습니다."
      );
    } finally {
      // 성공 / 실패 여부와 상관없이 작업 잠금 해제
      setIsSubmitting(false);
    }
  };

  const handlePublishSchedule = async () => {
  // 이미 다른 생성/공개/Shift 작업이 진행 중이면 중복 실행 방지
  if (isSubmitting) {
    return;
  }

  // 현재 화면의 주차가 최신 조회 결과인지,
  // 그리고 실제 DRAFT 상태인지 확인
  if (!isCurrentScheduleReady || status !== "DRAFT") {
    window.alert(
      "현재 주차의 근무표 상태를 확인 중입니다. 잠시 후 다시 시도해주세요."
    );
    return;
  }

  // 공개할 Schedule 자체가 없는 경우 방어
  if (scheduleId === null) {
    window.alert("근무표가 생성되지 않았습니다.");
    return;
  }

  // 실제 공개 API 요청
  // false 요청에서 SFT-015가 발생하면 사용자 확인 후 true로 재요청
  const submitPublish = async (
    confirmUnavailableConflict: boolean
  ) => {
    try {
      await publishSchedule(
        workplaceId,
        scheduleId,
        confirmUnavailableConflict
      );

      // 공개 성공 후 서버의 최신 PUBLISHED 상태를 다시 조회
      setScheduleReloadKey((prev) => prev + 1);
    } catch (err) {
      if (err instanceof ApiError) {
        const requiresConfirmation =
          typeof err.data === "object" &&
          err.data !== null &&
          "requiresConfirmation" in err.data &&
          err.data.requiresConfirmation === true;

        if (
          err.code === UNAVAILABLE_TIME_CONFLICT_CODE &&
          requiresConfirmation &&
          !confirmUnavailableConflict
        ) {
          const confirmed = window.confirm(
            `${err.message}\n\n그래도 근무표를 공개하시겠습니까?`
          );

          if (confirmed) {
            await submitPublish(true);
          }

          return;
        }

        window.alert(err.message);
        return;
      }

      window.alert(
        "근무표 공개 중 오류가 발생했습니다."
      );
    }
  };

  // 여기서부터 공개 작업 전체를 잠금
  setIsSubmitting(true);

  try {
    await submitPublish(false);
  } finally {
    // 성공 / 실패 / 사용자가 충돌 확인을 취소한 경우 모두 잠금 해제
    setIsSubmitting(false);
  }
};

  const openAddShiftModal = () => {
  if (
    isSubmitting ||
    !isCurrentScheduleReady ||
    !areCurrentMembersReady ||
    members.length === 0 ||
    status !== "DRAFT"
  ) {
    return;
  }

    setEditingShiftId(null);
    setSelectedMemberId(members[0].id);
    setSelectedDay("mon");
    setStartTime("09:00");
    setEndTime("18:00");
    setError("");
    setShowShiftModal(true);
  };

  const openEditShiftModal = (shift: Shift) => {
    if (
      isSubmitting ||
      !isCurrentScheduleReady ||
      !areCurrentMembersReady ||
      status !== "DRAFT"
    ) {
      return;
    }

    const memberExists = members.some(
      (member) => member.id === shift.memberId
    );

    if (!memberExists) {
      window.alert(
        "현재 구성원 목록에서 해당 근무자를 확인할 수 없습니다."
      );
      return;
    }

    setEditingShiftId(shift.id);
    setSelectedMemberId(shift.memberId);
    setSelectedDay(shift.day);
    setStartTime(shift.startTime);
    setEndTime(shift.endTime);
    setError("");
    setShowShiftModal(true);
  };

  const closeShiftModal = () => {
    if (isSubmitting) {
      return;
    }
    setShowShiftModal(false);
    setEditingShiftId(null);
    setError("");
  };

  const closeAfterSuccess = () => {
    setShowShiftModal(false);
    setEditingShiftId(null);
    setError("");
  };

  const submitCreateShift = async (
    confirmUnavailableConflict: boolean
  ) => {
    if (
      !isCurrentScheduleReady ||
      !areCurrentMembersReady ||
      status !== "DRAFT"
    ) {
      setError(
        "현재 주차의 근무표 상태를 확인한 후 다시 시도해주세요."
      );
      return;
    }
    if (scheduleId === null) {
      setError("근무표가 생성되지 않았습니다.");
      return;
    }

    const day = days.find((d) => d.key === selectedDay);
    const member = members.find((m) => m.id === selectedMemberId);

    if (!day || !member) {
      setError("직원 또는 날짜 정보를 확인해주세요.");
      return;
    }

    setIsSubmitting(true);
    setError("");

    try {
      const created = await createShift(workplaceId, scheduleId, {
        memberId: member.id,
        startAt: `${day.date}T${startTime}:00`,
        endAt: `${day.date}T${endTime}:00`,
        confirmUnavailableConflict,
      });

      // 서버가 내려준 값을 화면 표시용 Shift 형태로 변환해 반영
      const newShift: Shift = {
        id: created.shiftId,
        memberId: member.id,
        memberName: created.memberName,
        day: selectedDay,
        startTime,
        endTime,
        status: "SCHEDULED",
      };

      setShifts((prev) => [...prev, newShift]);
      closeAfterSuccess();
    } catch (err) {
      if (err instanceof ApiError) {
        // 직원의 불가능 시간과 겹치는 경우, 관리자에게 강행 여부를 확인
        if (
            err.code === UNAVAILABLE_TIME_CONFLICT_CODE &&
            !confirmUnavailableConflict
        ) {
          const confirmed = window.confirm(
              `${err.message}\n\n그래도 근무를 등록하시겠습니까?`
          );

          if (confirmed) {
            await submitCreateShift(true);
            return;
          }

          setError("근무 등록이 취소되었습니다.");
        } else {
          setError(err.message);
        }
      } else {
        setError("근무 등록 중 오류가 발생했습니다. 다시 시도해주세요.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSaveShift = async () => {
    if (isSubmitting) {
      return;
    }

    if (
      !isCurrentScheduleReady ||
      !areCurrentMembersReady ||
      status !== "DRAFT"
    ) {
      setError(
        "현재 주차의 근무표 상태를 확인한 후 다시 시도해주세요."
      );
      return;
    }

    if (startTime >= endTime) {
      setError("종료 시간은 시작 시간보다 늦어야 합니다.");
      return;
    }

    // 1. 근무 수정 (editingShiftId가 있는 경우)
    if (editingShiftId !== null) {
      if (scheduleId === null) {
        setError("근무표가 생성되지 않았습니다.");
        return;
      }

      const day = days.find((d) => d.key === selectedDay);
      const member = members.find((m) => m.id === selectedMemberId);

      if (!day || !member) {
        setError("직원 또는 날짜 정보를 확인해주세요.");
        return;
      }

      setIsSubmitting(true);
      setError("");

      //SFT-015 발생 시 재요청을 처리하기 위한 내부 함수 구현
      const submitUpdateShift = async (
        confirmUnavailableConflict: boolean
      ) => {
        try {
          const updated = await updateShift(
            workplaceId,
            scheduleId,
            editingShiftId,
            {
              memberId: member.id,
              startAt: `${day.date}T${startTime}:00`,
              endAt: `${day.date}T${endTime}:00`,
              confirmUnavailableConflict,
            }
          );

          setShifts((prevShifts) =>
            prevShifts.map((shift) =>
              shift.id === editingShiftId
                ? {
                    ...shift,
                    memberId: member.id,
                    memberName: updated.memberName,
                    day: selectedDay,
                    startTime,
                    endTime,
                  }
                : shift
            )
          );

          closeAfterSuccess();
        } catch (err) {
          if (err instanceof ApiError) {
            if (
              err.code === UNAVAILABLE_TIME_CONFLICT_CODE &&
              !confirmUnavailableConflict
            ) {
              const confirmed = window.confirm(
                `${err.message}\n\n그래도 근무를 수정하시겠습니까?`
              );

              if (confirmed) {
                await submitUpdateShift(true);
                return;
              }

              setError("근무 수정이 취소되었습니다.");
            } else {
              setError(err.message);
            }
          } else {
            setError("수정 중 오류가 발생했습니다.");
          }
        }
      };

      try {
        await submitUpdateShift(false);
      } finally {
        setIsSubmitting(false);
      }
      return;
    }

    // 2. 새 근무 추가 (editingShiftId가 null인 경우)
    await submitCreateShift(false);
  };

 //근무 삭제 핸들러 구현
  const handleDeleteShift = async () => {
    if (isSubmitting) {
      return;
    }

    if (!isCurrentScheduleReady || status !== "DRAFT") {
      setError(
        "현재 주차의 근무표 상태를 확인한 후 다시 시도해주세요."
      );
      return;
    }

    if (editingShiftId === null || scheduleId === null) return;

    if (!window.confirm("정말로 이 근무를 삭제하시겠습니까?")) return;

    setIsSubmitting(true);
    setError("");

    try {
      await deleteShift(workplaceId, scheduleId, editingShiftId);

      // 삭제된 근무 State에서 제거
      setShifts((prev) => prev.filter((shift) => shift.id !== editingShiftId));
      closeAfterSuccess();
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("삭제 중 오류가 발생했습니다.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const openPublishedCreateShiftModal = () => {
    if (
      isSubmitting ||
      !isCurrentScheduleReady ||
      !areCurrentMembersReady ||
      members.length === 0 ||
      status !== "PUBLISHED"
    ) {
      return;
    }

    setPublishedCreateMemberId(members[0].id);
    setPublishedCreateDay("mon");
    setPublishedCreateStartTime("09:00");
    setPublishedCreateEndTime("18:00");
    setPublishedCreateError("");
    setPublishedCreateConflicts([]);
    setShowPublishedCreateShiftModal(true);
  };

  const closePublishedCreateShiftModal = () => {
    if (isSubmitting) {
      return;
    }

    setShowPublishedCreateShiftModal(false);
    setPublishedCreateError("");
    setPublishedCreateConflicts([]);
  };

  const resetPublishedCreateConfirmation = () => {
    setPublishedCreateConflicts([]);
    setPublishedCreateError("");
  };

  const submitPublishedCreateShift = async (acknowledgeConflicts: boolean) => {
    if (
      isSubmitting ||
      !isCurrentScheduleReady ||
      status !== "PUBLISHED" ||
      scheduleId === null ||
      publishedCreateMemberId === null
    ) {
      return;
    }

    if (publishedCreateStartTime >= publishedCreateEndTime) {
      setPublishedCreateError("종료 시간은 시작 시간보다 늦어야 합니다.");
      return;
    }

    const day = days.find((item) => item.key === publishedCreateDay);
    if (!day) {
      setPublishedCreateError("날짜 정보를 확인해주세요.");
      return;
    }

    const startAt = `${day.date}T${publishedCreateStartTime}:00`;
    const endAt = `${day.date}T${publishedCreateEndTime}:00`;
    const acknowledgedUnavailableConflicts = acknowledgeConflicts
      ? publishedCreateConflicts.map((conflict) => ({
          newShiftKey: conflict.newShiftKey,
          overlaps: conflict.overlaps,
        }))
      : [];

    setIsSubmitting(true);
    setPublishedCreateError("");

    try {
      await createPublishedShift(workplaceId, scheduleId, {
        memberId: publishedCreateMemberId,
        startAt,
        endAt,
        acknowledgedUnavailableConflicts,
      });
      setShowPublishedCreateShiftModal(false);
      setPublishedCreateConflicts([]);
      setScheduleReloadKey((prev) => prev + 1);
    } catch (err) {
      if (
        err instanceof ApiError &&
        err.code === PUBLISHED_UNAVAILABLE_CONFIRMATION_CODE &&
        !acknowledgeConflicts &&
        typeof err.data === "object" &&
        err.data !== null &&
        "requiresConfirmation" in err.data &&
        err.data.requiresConfirmation === true &&
        "unavailableConflicts" in err.data &&
        Array.isArray(err.data.unavailableConflicts)
      ) {
        setPublishedCreateConflicts(
          err.data.unavailableConflicts as NewShiftUnavailableConflict[]
        );
        setPublishedCreateError(
          "선택한 직원의 근무 불가능 시간과 겹칩니다. 아래 구간을 확인해주세요."
        );
      } else if (err instanceof ApiError) {
        setPublishedCreateError(err.message);
      } else {
        setPublishedCreateError("공개 근무 추가 중 오류가 발생했습니다. 다시 시도해주세요.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const openPublishedShiftModal = (shift: Shift) => {
    if (
      isSubmitting ||
      !isCurrentScheduleReady ||
      status !== "PUBLISHED" ||
      shift.status !== "SCHEDULED"
    ) {
      return;
    }

    setSelectedPublishedShift(shift);
    setSelectedReassignMemberId(
      members.find((member) => member.id !== shift.memberId)?.id ?? null
    );
    setPublishedShiftError("");
    setUnavailableConflicts([]);
    setShowPublishedShiftModal(true);
  };

  const closePublishedShiftModal = () => {
    setShowPublishedShiftModal(false);
    setSelectedPublishedShift(null);
    setSelectedReassignMemberId(null);
    setPublishedShiftError("");
    setUnavailableConflicts([]);
  };

  const refreshScheduleAfterPublishedChange = () => {
    setScheduleReloadKey((prev) => prev + 1);
  };

  const handlePublishedShiftError = (err: unknown) => {
    if (!(err instanceof ApiError)) {
      setPublishedShiftError("요청 처리 중 오류가 발생했습니다. 다시 시도해주세요.");
      return;
    }

    if (err.code === PUBLISHED_SHIFT_CHANGE_NOT_ALLOWED_CODE) {
      window.alert("이미 시작했거나 취소된 근무는 변경할 수 없습니다. 최신 근무표를 다시 불러옵니다.");
      closePublishedShiftModal();
      refreshScheduleAfterPublishedChange();
      return;
    }

    if (
      SHIFT_OVERLAP_CODES.includes(err.code) ||
      err.code === SAME_SHIFT_MEMBER_REASSIGNMENT_CODE
    ) {
      setPublishedShiftError(err.message);
      return;
    }

    setPublishedShiftError(err.message);
  };

  const handleCancelPublishedShift = async () => {
    if (
      isSubmitting ||
      !isCurrentScheduleReady ||
      status !== "PUBLISHED" ||
      scheduleId === null ||
      selectedPublishedShift === null
    ) {
      return;
    }

    const confirmed = window.confirm(
      `${selectedPublishedShift.memberName}님의 근무를 취소하시겠습니까?`
    );

    if (!confirmed) {
      return;
    }

    setIsSubmitting(true);
    setPublishedShiftError("");

    try {
      await cancelPublishedShift(workplaceId, scheduleId, selectedPublishedShift.id);
      closePublishedShiftModal();
      refreshScheduleAfterPublishedChange();
    } catch (err) {
      handlePublishedShiftError(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const submitPublishedReassignment = async (acknowledgeConflicts: boolean) => {
    if (
      isSubmitting ||
      !isCurrentScheduleReady ||
      status !== "PUBLISHED" ||
      scheduleId === null ||
      selectedPublishedShift === null ||
      selectedReassignMemberId === null
    ) {
      return;
    }

    const acknowledgedUnavailableConflicts = acknowledgeConflicts
      ? unavailableConflicts.map((conflict) => ({
          shiftId: conflict.shiftId,
          targetMemberId: conflict.targetMemberId,
          overlaps: conflict.overlaps,
        }))
      : [];

    setIsSubmitting(true);
    setPublishedShiftError("");

    try {
      await reassignPublishedShift(
        workplaceId,
        scheduleId,
        selectedPublishedShift.id,
        selectedReassignMemberId,
        acknowledgedUnavailableConflicts
      );
      closePublishedShiftModal();
      refreshScheduleAfterPublishedChange();
    } catch (err) {
      if (
        err instanceof ApiError &&
        err.code === PUBLISHED_UNAVAILABLE_CONFIRMATION_CODE &&
        !acknowledgeConflicts &&
        typeof err.data === "object" &&
        err.data !== null &&
        "requiresConfirmation" in err.data &&
        err.data.requiresConfirmation === true &&
        "unavailableConflicts" in err.data &&
        Array.isArray(err.data.unavailableConflicts)
      ) {
        setUnavailableConflicts(err.data.unavailableConflicts as UnavailableConflict[]);
        setPublishedShiftError("선택한 직원의 근무 불가능 시간과 겹칩니다. 아래 구간을 확인해주세요.");
      } else {
        handlePublishedShiftError(err);
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReassignPublishedShift = async () => {
    if (selectedReassignMemberId === null) {
      setPublishedShiftError("재배정할 직원을 선택해주세요.");
      return;
    }

    await submitPublishedReassignment(false);
  };
  return (
      <>
        <PageHeader
            title="주간 근무표"
            description="이번 주 근무 일정을 확인하고 관리하세요."
        >
          {isCurrentScheduleReady && status === "NONE" && (
              <button
                type="button"
                onClick={handleCreateSchedule}
                disabled={!isCurrentScheduleReady || isSubmitting}
                className="rounded-xl bg-[#005642] px-4 py-2.5 text-sm font-bold text-white transition hover:bg-[#0b6b52] disabled:bg-[#dce8e2] disabled:text-[#78847f]"
              >
                이번 주 근무표 생성
              </button>
          )}

          {isCurrentScheduleReady && status === "DRAFT" && (
              <div className="flex gap-2">
                <button
                    type="button"
                    onClick={openAddShiftModal}
                    disabled={
                      !isCurrentScheduleReady ||
                      !areCurrentMembersReady ||
                      members.length === 0 ||
                      isSubmitting
                    }
                    className="rounded-xl border border-[#dce8e2] bg-white px-4 py-2.5 text-sm font-bold text-[#005642] transition hover:bg-[#f3fbf7]"
                >
                  + 근무 추가
                </button>

                <button
                    type="button"
                    onClick={handlePublishSchedule}
                    disabled={!isCurrentScheduleReady || isSubmitting}
                    className="rounded-xl bg-[#005642] px-4 py-2.5 text-sm font-bold text-white transition hover:bg-[#0b6b52]"
                >
                  공개하기
                </button>
              </div>
          )}

          {isCurrentScheduleReady && status === "PUBLISHED" && (
              <button
                  type="button"
                  onClick={openPublishedCreateShiftModal}
                  disabled={
                    !areCurrentMembersReady ||
                    members.length === 0 ||
                    isSubmitting
                  }
                  className="rounded-xl border border-[#dce8e2] bg-white px-4 py-2.5 text-sm font-bold text-[#005642] transition hover:bg-[#f3fbf7] disabled:cursor-not-allowed disabled:opacity-50"
              >
                + 근무 추가
              </button>
          )}

        </PageHeader>

        {isCurrentScheduleReady &&
          status === "DRAFT" &&
          memberLoadStatus === "loading" && (
            <div className="mb-4 rounded-xl border border-[#dce8e2] bg-[#fafdfb] px-4 py-3">
              <p className="text-sm font-semibold text-[#78847f]">
                근무에 배정할 구성원을 불러오는 중입니다...
              </p>
            </div>
        )}

        {isCurrentScheduleReady &&
          status === "DRAFT" &&
          memberLoadStatus === "error" && (
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#e6caca] bg-[#fffafa] px-4 py-3">
              <p className="text-sm font-semibold text-[#d95555]">
                구성원 목록을 불러오지 못했습니다.
              </p>

              <button
                type="button"
                onClick={handleRetryMembers}
                disabled={isSubmitting}
                className="rounded-lg border border-[#dce8e2] bg-white px-3 py-2 text-sm font-bold text-[#005642] transition hover:bg-[#f3fbf7] disabled:cursor-not-allowed disabled:opacity-50"
              >
                다시 시도
              </button>
            </div>
          )}

        {isCurrentScheduleReady &&
          status === "DRAFT" &&
          areCurrentMembersReady &&
          members.length === 0 && (
            <div className="mb-4 rounded-xl border border-[#dce8e2] bg-[#fafdfb] px-4 py-3">
              <p className="text-sm font-semibold text-[#78847f]">
                현재 근무에 배정할 수 있는 구성원이 없습니다.
              </p>
            </div>
          )}

        <Card>
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <button
                  type="button"
                  onClick={handlePreviousWeek}
                  disabled={isSubmitting || !isCurrentScheduleReady}
                  className="grid h-9 w-9 place-items-center rounded-lg border border-[#dce8e2] text-[#66736d] transition hover:bg-[#f3fbf7]"
              >
                <span className="block -translate-y-px text-xl leading-none">‹</span>
              </button>

              <p className="font-bold">
                {weekStartDate} ~ {addDays(weekStartDate, 6)}
              </p>

              <button
                  type="button"
                  onClick={handleNextWeek}
                  disabled={isSubmitting || !isCurrentScheduleReady}
                  className="grid h-9 w-9 place-items-center rounded-lg border border-[#dce8e2] text-[#66736d] transition hover:bg-[#f3fbf7]"
              >
                <span className="block -translate-y-px text-xl leading-none">›</span>
              </button>

              {isCurrentScheduleReady && status === "DRAFT" && (
                  <span className="ml-2 rounded-full bg-[#fff1d7] px-3 py-1 text-xs font-bold text-[#a96d09]">
                미공개
              </span>
              )}

              {isCurrentScheduleReady && status === "PUBLISHED" && (
                  <span className="ml-2 rounded-full bg-[#dff7ec] px-3 py-1 text-xs font-bold text-[#14956c]">
                공개됨
              </span>
              )}
            </div>

            {isCurrentScheduleReady && status === "DRAFT" && (
                <p className="text-xs text-[#78847f]">
                  직원에게 공개되기 전 근무표입니다.
                </p>
            )}

            {isCurrentScheduleReady && status === "PUBLISHED" && (
                <p className="text-xs font-semibold text-[#14956c]">
                  직원에게 공개된 근무표입니다.
                </p>
            )}
          </div>

          {!isCurrentScheduleReady ? (
            scheduleLoadStatus === "error" ? (
            <div className="flex min-h-[420px] flex-col items-center justify-center rounded-xl border border-dashed border-[#e6caca] bg-[#fffafa] px-4 text-center">
              <div className="text-4xl">⚠️</div>

              <h2 className="mt-4 text-lg font-black">
                근무표를 불러오지 못했습니다.
                </h2>

                <p className="mt-2 max-w-md text-sm leading-6 text-[#78847f]">
                  현재 주차의 근무표 정보를 다시 불러와주세요.
                  </p>

                <button
                 type="button"
                 onClick={handleRetrySchedule}
                 disabled={isSubmitting}
                 className="mt-5 rounded-xl bg-[#005642] px-5 py-3 text-sm font-bold text-white transition hover:bg-[#0b6b52] disabled:cursor-not-allowed disabled:opacity-50"
                >
                  다시 시도
                </button>
                </div>
              ) : (
              <div className="flex min-h-[420px] items-center justify-center rounded-xl border border-[#dce8e2] bg-[#fafdfb] px-4 text-center">
                <p className="text-sm font-semibold text-[#78847f]">
                  근무표를 불러오는 중입니다...
                </p>
              </div>
            )
          ) : status === "NONE" ? (
              <div className="flex min-h-[420px] flex-col items-center justify-center rounded-xl border border-dashed border-[#c9ded4] bg-[#fafdfb] px-4 text-center">
                <div className="text-4xl">📅</div>

                <h2 className="mt-4 text-lg font-black">
                  아직 이번 주 근무표가 없어요.
                </h2>

                <p className="mt-2 max-w-md text-sm leading-6 text-[#78847f]">
                  등록된 정기 근무를 기준으로 이번 주 근무표 초안을 생성할 수
                  있습니다.
                </p>

                <button
                    type="button"
                    onClick={handleCreateSchedule}
                    disabled={!isCurrentScheduleReady || isSubmitting}
                    className="mt-5 rounded-xl bg-[#005642] px-5 py-3 text-sm font-bold text-white transition hover:bg-[#0b6b52]"
                >
                  이번 주 근무표 생성
                </button>
              </div>
          ) : (
              <>
                <div className="overflow-x-auto">
                  <div className="min-w-[1050px] overflow-hidden rounded-xl border border-[#dce8e2]">
                    {/* 날짜 헤더 */}
                    <div className="grid grid-cols-[80px_repeat(7,1fr)] bg-[#fafcfb]">
                      <div className="border-r border-[#edf2ef] p-3" />

                      {days.map((day) => (
                          <div
                              key={day.key}
                              className="border-r border-[#edf2ef] p-3 text-center text-sm font-bold last:border-r-0"
                          >
                            {day.label}
                          </div>
                      ))}
                    </div>

                    {/* 시간표 */}
                    <div className="grid grid-cols-[80px_repeat(7,1fr)]">
                      {/* 시간 영역 */}
                      <div
                          className="relative border-r border-[#edf2ef] bg-[#fafcfb]"
                          style={{
                            height: (END_HOUR - START_HOUR) * HOUR_HEIGHT,
                          }}
                      >
                        {timeLabels.map((time, index) => (
                            <div
                                key={time}
                                className="absolute right-3 -translate-y-1/2 text-xs font-semibold text-[#78847f]"
                                style={{ top: index * HOUR_HEIGHT }}
                            >
                              {time}
                            </div>
                        ))}
                      </div>

                      {/* 요일 영역 */}
                      {days.map((day) => {
                        const dayShifts = getPositionedShifts(
                            shifts.filter((shift) => shift.day === day.key)
                        );

                        return (
                            <div
                                key={day.key}
                                className="relative border-r border-[#edf2ef] last:border-r-0"
                                style={{
                                  height: (END_HOUR - START_HOUR) * HOUR_HEIGHT,
                                }}
                            >
                              {/* 시간 구분선 */}
                              {timeLabels.map((time, index) => (
                                  <div
                                      key={time}
                                      className="absolute left-0 right-0 border-t border-[#edf2ef]"
                                      style={{ top: index * HOUR_HEIGHT }}
                                  />
                              ))}

                              {/* 근무 카드 */}
                              {dayShifts.map((shift) => (
                                  <button
                                      key={shift.id}
                                      type="button"
                                      onClick={() => {
                                        if (status === "DRAFT") {
                                          openEditShiftModal(shift);
                                          return;
                                        }

                                        openPublishedShiftModal(shift);
                                      }}
                                      disabled={
                                        isSubmitting ||
                                        !isCurrentScheduleReady ||
                                        shift.status === "CANCELLED" ||
                                        (status === "DRAFT" && !areCurrentMembersReady)
                                      }
                                      style={getShiftStyle(shift)}
                                      className={`absolute z-10 overflow-hidden rounded-lg px-2 py-2 text-left shadow-sm transition disabled:cursor-default ${
                                        shift.status === "CANCELLED"
                                          ? "border border-[#e6caca] bg-[#fffafa] text-[#a85b5b]"
                                          : "border border-[#bde5d3] bg-[#dff7ec] text-[#005642] hover:bg-[#cceedd] disabled:hover:bg-[#dff7ec]"
                                      }`}
                                  >
                                    <p className="truncate text-sm font-black">
                                      {shift.memberName}
                                    </p>

                                    <p className="mt-1 text-[11px] font-semibold text-[#397660]">
                                      {shift.startTime} ~ {shift.endTime}
                                    </p>

                                    {status === "DRAFT" && (
                                        <p className="mt-2 text-[10px] text-[#5d8f7b]">
                                          클릭하여 수정
                                        </p>
                                    )}

                                    {status === "PUBLISHED" && shift.status === "SCHEDULED" && (
                                        <p className="mt-2 text-[10px] text-[#5d8f7b]">
                                          클릭하여 관리
                                        </p>
                                    )}

                                    {shift.status === "CANCELLED" && (
                                        <p className="mt-2 text-[10px] text-[#a85b5b]">
                                          취소됨
                                        </p>
                                    )}
                                  </button>
                              ))}
                            </div>
                        );
                      })}
                    </div>
                  </div>
                </div>

                <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
                  <div className="flex gap-4 text-xs text-[#78847f]">
                    <div className="flex items-center gap-2">
                      <span className="h-3 w-3 rounded-sm bg-[#dff7ec]" />
                      등록된 근무
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="h-3 w-3 rounded-sm border border-[#dce8e2] bg-white" />
                      근무 없음
                    </div>
                  </div>

                  {status === "DRAFT" && (
                      <p className="text-xs text-[#78847f]">
                        근무 카드를 클릭하면 내용을 수정할 수 있어요.
                      </p>
                  )}

                  {status === "PUBLISHED" && (
                      <p className="text-xs font-semibold text-[#14956c]">
                        근무 카드를 클릭하면 취소하거나 담당자를 변경할 수 있어요.
                      </p>
                  )}
                </div>
              </>
          )}
        </Card>

        {/* 근무 추가 / 수정 모달 */}
        {showShiftModal && isCurrentScheduleReady && status === "DRAFT" && (
            <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 px-4">
              <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
                <div className="flex items-start justify-between">
                  <div>
                    <h2 className="text-xl font-black">
                      {editingShiftId !== null ? "근무 수정" : "근무 추가"}
                    </h2>

                    <p className="mt-1 text-sm text-[#78847f]">
                      {editingShiftId !== null
                          ? "근무자와 근무 시간을 수정해주세요."
                          : "근무자와 근무 시간을 선택해주세요."}
                    </p>
                  </div>

                  <button
                      type="button"
                      onClick={closeShiftModal}
                      className="text-xl text-[#78847f] hover:text-black"
                  >
                    ×
                  </button>
                </div>

                <div className="mt-6 space-y-5">
                  <div>
                    <label className="mb-2 block text-sm font-bold">
                      직원
                    </label>

                    <select
                        value={selectedMemberId ?? ""}
                        onChange={(e) => setSelectedMemberId(Number(e.target.value))}
                        disabled={isSubmitting}
                        className="w-full rounded-xl border border-[#dce8e2] bg-white px-4 py-3 outline-none focus:border-[#14956c]"
                    >
                      {members.map((member) => (
                          <option key={member.id} value={member.id}>
                            {member.name}
                          </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-bold">
                      날짜
                    </label>

                    <select
                        value={selectedDay}
                        onChange={(e) => setSelectedDay(e.target.value as DayKey)}
                        disabled={isSubmitting}
                        className="w-full rounded-xl border border-[#dce8e2] bg-white px-4 py-3 outline-none focus:border-[#14956c]"
                    >
                      {days.map((day) => (
                          <option key={day.key} value={day.key}>
                            {day.label}
                          </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-bold">
                      근무 시간
                    </label>

                    <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
                      <input
                          type="time"
                          value={startTime}
                          onChange={(e) => setStartTime(e.target.value)}
                          disabled={isSubmitting}
                          className="rounded-xl border border-[#dce8e2] px-4 py-3 outline-none focus:border-[#14956c]"
                      />

                      <span className="text-[#78847f]">~</span>

                      <input
                          type="time"
                          value={endTime}
                          onChange={(e) => setEndTime(e.target.value)}
                          disabled={isSubmitting}
                          className="rounded-xl border border-[#dce8e2] px-4 py-3 outline-none focus:border-[#14956c]"
                      />
                    </div>
                  </div>

                  {error && (
                      <p className="text-sm font-semibold text-[#d95555]">
                        {error}
                      </p>
                  )}
                </div>

                <div className="mt-6 flex gap-3">
                  {/* 수정 모드일 때만 '삭제' 버튼 표시 */}
                  {editingShiftId !== null && (
                      <button
                          type="button"
                          onClick={handleDeleteShift}
                          disabled={isSubmitting}
                          className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-bold text-red-600 transition hover:bg-red-100 disabled:opacity-50"
                      >
                        삭제
                      </button>
                  )}
                  <button
                      type="button"
                      onClick={closeShiftModal}
                      disabled={isSubmitting}
                      className="flex-1 rounded-xl border border-[#dce8e2] px-4 py-3 text-sm font-bold text-[#66736d] transition hover:bg-[#f3fbf7]"
                  >
                    취소
                  </button>

                  <button
                      type="button"
                      onClick={handleSaveShift}
                      disabled={isSubmitting}
                      className="flex-1 rounded-xl bg-[#005642] px-4 py-3 text-sm font-bold text-white transition hover:bg-[#0b6b52]"
                  >
                    {isSubmitting ? "처리 중..." : editingShiftId !== null
                            ? "수정 완료" : "근무 추가"}
                  </button>
                </div>
              </div>
            </div>
        )}

        {showPublishedCreateShiftModal &&
          isCurrentScheduleReady &&
          status === "PUBLISHED" && (
            <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 px-4">
              <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
                <div className="flex items-start justify-between">
                  <div>
                    <h2 className="text-xl font-black">공개 근무 추가</h2>
                    <p className="mt-1 text-sm text-[#78847f]">
                      직원에게 즉시 공개되는 근무를 추가합니다.
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={closePublishedCreateShiftModal}
                    disabled={isSubmitting}
                    className="text-xl text-[#78847f] hover:text-black disabled:cursor-not-allowed disabled:opacity-50"
                    aria-label="공개 근무 추가 창 닫기"
                  >
                    ×
                  </button>
                </div>

                <div className="mt-6 space-y-5">
                  <div>
                    <label className="mb-2 block text-sm font-bold">직원</label>
                    <select
                      value={publishedCreateMemberId ?? ""}
                      onChange={(event) => {
                        setPublishedCreateMemberId(Number(event.target.value));
                        resetPublishedCreateConfirmation();
                      }}
                      disabled={isSubmitting || publishedCreateConflicts.length > 0}
                      className="w-full rounded-xl border border-[#dce8e2] bg-white px-4 py-3 outline-none focus:border-[#14956c] disabled:cursor-not-allowed disabled:bg-[#fafcfb]"
                    >
                      {members.map((member) => (
                        <option key={member.id} value={member.id}>
                          {member.name}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-bold">날짜</label>
                    <select
                      value={publishedCreateDay}
                      onChange={(event) => {
                        setPublishedCreateDay(event.target.value as DayKey);
                        resetPublishedCreateConfirmation();
                      }}
                      disabled={isSubmitting || publishedCreateConflicts.length > 0}
                      className="w-full rounded-xl border border-[#dce8e2] bg-white px-4 py-3 outline-none focus:border-[#14956c] disabled:cursor-not-allowed disabled:bg-[#fafcfb]"
                    >
                      {days.map((day) => (
                        <option key={day.key} value={day.key}>
                          {day.label}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="mb-2 block text-sm font-bold">근무 시간</label>
                    <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
                      <input
                        type="time"
                        value={publishedCreateStartTime}
                        onChange={(event) => {
                          setPublishedCreateStartTime(event.target.value);
                          resetPublishedCreateConfirmation();
                        }}
                        disabled={isSubmitting || publishedCreateConflicts.length > 0}
                        className="rounded-xl border border-[#dce8e2] px-4 py-3 outline-none focus:border-[#14956c] disabled:cursor-not-allowed disabled:bg-[#fafcfb]"
                      />

                      <span className="text-[#78847f]">~</span>

                      <input
                        type="time"
                        value={publishedCreateEndTime}
                        onChange={(event) => {
                          setPublishedCreateEndTime(event.target.value);
                          resetPublishedCreateConfirmation();
                        }}
                        disabled={isSubmitting || publishedCreateConflicts.length > 0}
                        className="rounded-xl border border-[#dce8e2] px-4 py-3 outline-none focus:border-[#14956c] disabled:cursor-not-allowed disabled:bg-[#fafcfb]"
                      />
                    </div>
                  </div>

                  {publishedCreateConflicts.length > 0 && (
                    <div className="rounded-xl border border-[#f0cf8b] bg-[#fff9eb] p-4">
                      <p className="text-sm font-bold text-[#8a5c00]">근무 불가능 시간 충돌</p>
                      <ul className="mt-2 space-y-1 text-xs leading-5 text-[#78633a]">
                        {publishedCreateConflicts.flatMap((conflict) =>
                          conflict.overlaps.map((overlap) => (
                            <li key={`${conflict.newShiftKey.scheduleId}-${overlap.startAt}-${overlap.endAt}`}>
                              {conflict.targetName}: {overlap.startAt.replace("T", " ")} ~ {overlap.endAt.replace("T", " ")}
                            </li>
                          ))
                        )}
                      </ul>
                    </div>
                  )}

                  {publishedCreateError && (
                    <p className="text-sm font-semibold text-[#d95555]">
                      {publishedCreateError}
                    </p>
                  )}
                </div>

                <div className="mt-6 flex gap-3">
                  <button
                    type="button"
                    onClick={closePublishedCreateShiftModal}
                    disabled={isSubmitting}
                    className="flex-1 rounded-xl border border-[#dce8e2] px-4 py-3 text-sm font-bold text-[#66736d] transition hover:bg-[#f3fbf7] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    취소
                  </button>

                  {publishedCreateConflicts.length > 0 ? (
                    <button
                      type="button"
                      onClick={() => void submitPublishedCreateShift(true)}
                      disabled={isSubmitting}
                      className="flex-1 rounded-xl bg-[#8a5c00] px-4 py-3 text-sm font-bold text-white transition hover:bg-[#704a00] disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      충돌을 확인하고 추가
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => void submitPublishedCreateShift(false)}
                      disabled={isSubmitting}
                      className="flex-1 rounded-xl bg-[#005642] px-4 py-3 text-sm font-bold text-white transition hover:bg-[#0b6b52] disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      {isSubmitting ? "처리 중..." : "공개 근무 추가"}
                    </button>
                  )}
                </div>
              </div>
            </div>
        )}

        {showPublishedShiftModal &&
          isCurrentScheduleReady &&
          status === "PUBLISHED" &&
          selectedPublishedShift !== null && (
            <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 px-4">
              <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <h2 className="text-xl font-black">공개 근무 관리</h2>
                    <p className="mt-1 text-sm text-[#78847f]">
                      {selectedPublishedShift.memberName} · {selectedPublishedShift.startTime} ~ {selectedPublishedShift.endTime}
                    </p>
                  </div>

                  <button
                    type="button"
                    onClick={closePublishedShiftModal}
                    disabled={isSubmitting}
                    className="text-xl text-[#78847f] hover:text-black disabled:cursor-not-allowed disabled:opacity-50"
                    aria-label="공개 근무 관리 창 닫기"
                  >
                    ×
                  </button>
                </div>

                <div className="mt-6 space-y-5">
                  <div className="rounded-xl border border-[#dce8e2] bg-[#fafdfb] p-4">
                    <p className="text-sm font-bold">근무 취소</p>
                    <p className="mt-1 text-xs leading-5 text-[#78847f]">
                      시작 전인 공개 근무만 취소할 수 있습니다. 취소 전 다시 확인합니다.
                    </p>
                    <button
                      type="button"
                      onClick={handleCancelPublishedShift}
                      disabled={isSubmitting}
                      className="mt-3 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-bold text-red-600 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-50"
                    >
                      근무 취소
                    </button>
                  </div>

                  <div className="border-t border-[#edf2ef] pt-5">
                    <p className="text-sm font-bold">담당자 재배정</p>
                    <p className="mt-1 text-xs leading-5 text-[#78847f]">
                      현재 이 근무지에 소속된 구성원만 선택할 수 있습니다.
                    </p>

                    {memberLoadStatus === "loading" && (
                      <p className="mt-3 text-sm text-[#78847f]">구성원 목록을 불러오는 중입니다...</p>
                    )}

                    {memberLoadStatus === "error" && (
                      <div className="mt-3 flex items-center justify-between gap-3">
                        <p className="text-sm font-semibold text-[#d95555]">구성원 목록을 불러오지 못했습니다.</p>
                        <button
                          type="button"
                          onClick={handleRetryMembers}
                          disabled={isSubmitting}
                          className="rounded-lg border border-[#dce8e2] px-3 py-2 text-sm font-bold text-[#005642] disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          다시 시도
                        </button>
                      </div>
                    )}

                    {areCurrentMembersReady && (
                      <>
                        <select
                          value={selectedReassignMemberId ?? ""}
                          onChange={(event) => {
                            setSelectedReassignMemberId(Number(event.target.value));
                            setUnavailableConflicts([]);
                            setPublishedShiftError("");
                          }}
                          disabled={isSubmitting || members.every((member) => member.id === selectedPublishedShift.memberId)}
                          className="mt-3 w-full rounded-xl border border-[#dce8e2] bg-white px-4 py-3 outline-none focus:border-[#14956c] disabled:cursor-not-allowed disabled:bg-[#fafcfb]"
                        >
                          {members
                            .filter((member) => member.id !== selectedPublishedShift.memberId)
                            .map((member) => (
                              <option key={member.id} value={member.id}>
                                {member.name}
                              </option>
                            ))}
                        </select>

                        {members.every((member) => member.id === selectedPublishedShift.memberId) && (
                          <p className="mt-2 text-sm text-[#78847f]">
                            재배정할 다른 현재 소속 구성원이 없습니다.
                          </p>
                        )}

                        {unavailableConflicts.length > 0 && (
                          <div className="mt-3 rounded-xl border border-[#f0cf8b] bg-[#fff9eb] p-4">
                            <p className="text-sm font-bold text-[#8a5c00]">근무 불가능 시간 충돌</p>
                            <ul className="mt-2 space-y-1 text-xs leading-5 text-[#78633a]">
                              {unavailableConflicts.flatMap((conflict) =>
                                conflict.overlaps.map((overlap) => (
                                  <li key={`${conflict.shiftId}-${overlap.startAt}-${overlap.endAt}`}>
                                    {conflict.targetName}: {overlap.startAt.replace("T", " ")} ~ {overlap.endAt.replace("T", " ")}
                                  </li>
                                ))
                              )}
                            </ul>
                            <button
                              type="button"
                              onClick={() => void submitPublishedReassignment(true)}
                              disabled={isSubmitting}
                              className="mt-3 rounded-lg bg-[#8a5c00] px-3 py-2 text-sm font-bold text-white transition hover:bg-[#704a00] disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              충돌을 확인하고 재배정
                            </button>
                          </div>
                        )}

                        <button
                          type="button"
                          onClick={() => void handleReassignPublishedShift()}
                          disabled={
                            isSubmitting ||
                            selectedReassignMemberId === null ||
                            unavailableConflicts.length > 0
                          }
                          className="mt-3 rounded-lg bg-[#005642] px-3 py-2 text-sm font-bold text-white transition hover:bg-[#0b6b52] disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          담당자 재배정
                        </button>
                      </>
                    )}
                  </div>

                  {publishedShiftError && (
                    <p className="text-sm font-semibold text-[#d95555]">
                      {publishedShiftError}
                    </p>
                  )}
                </div>
              </div>
            </div>
        )}
      </>
  );
}
