"use client";

import {useEffect, useState, useCallback} from "react";
import Link from "next/link";
import Card from "@/components/ui/Card";
import { useParams } from "next/navigation";
import PageHeader from "@/components/ui/PageHeader";
import { apiFetch, ApiError } from "@/lib/api";

export type RequestStatus = "OPEN" | "ACCEPTED" | "APPROVED" | "CLOSED";

// DTO
export type AcceptedMemberResponse = {
  memberId: number;
  name: string;
};

export type SubstituteRequestItem = {
  requestId: number;
  shiftId: number;
  requesterMemberId: number;
  requesterName: string;
  workplaceId: number;
  workplaceName: string;
  startAt: string;
  endAt: string;
  status: RequestStatus;
  createdAt: string;
  acceptedMember: AcceptedMemberResponse | null;
};

// 끝난 요청 (API-SB-02). 서버는 판단하지 않고 사실 Field만 줌, 표시 규칙은 FRONTEND-CONTRACT §3-4
type CloseReason =
    | "ALL_CANDIDATES_REJECTED"
    | "EXPIRED"
    | "MANAGER_CLOSED"
    | "SHIFT_CHANGED"
    | "CANDIDATE_INELIGIBLE";

type ClosedSubstituteRequestItem = {
  requestId: number;
  shiftId: number;
  startAt: string;
  endAt: string;
  shiftStatus: "SCHEDULED" | "CANCELLED";
  outcome: "CLOSED" | "EXPIRED";
  closeReason: CloseReason | null;
  closedAt: string; // 효력 종료 시각 (만료는 근무 시작 시각)
  requesterName: string;
  requesterMembershipEnded: boolean;
  accepterName: string | null;
  requesterStillAssigned: boolean;
  shiftStarted: boolean;
  hasNewerActiveRequest: boolean;
};

type ClosedSubstituteRequestPage = {
  items: ClosedSubstituteRequestItem[];
  page: number;
  size: number;
  hasNext: boolean;
};

type ClosedResultView = {
  label: string;
  badgeClassName: string;
};

type Tab = "WAITING" | "APPROVAL" | "CLOSED";

const RED_BADGE = "bg-[#ffeaea] text-[#d95555]";
const GRAY_BADGE = "bg-[#f3f5f4] text-[#66736d]";

// 서버의 LocalDateTime 문자열을 KST(UTC+9) Date 객체로 해석
function parseAsKst(isoString: string | null): Date | null {
  if (!isoString) return null;
  const hasTimeZone = /[Z+-]\d{2}:?\d{2}$/.test(isoString);
  const kstFormattedString = hasTimeZone ? isoString : `${isoString}+09:00`;
  return new Date(kstFormattedString);
}

// KST 시간 포맷팅 (HH:mm)
function formatKstTime(date: Date): string {
  const formatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = formatter.formatToParts(date);
  const hour = parts.find((p) => p.type === "hour")?.value || "00";
  const minute = parts.find((p) => p.type === "minute")?.value || "00";
  return `${hour}:${minute}`;
}

// 대체 근무 일시 포맷팅 (YYYY.MM.DD (요일) / HH:mm - HH:mm)
function formatShiftDateTime(startAtStr: string, endAtStr: string) {
  const startDate = parseAsKst(startAtStr);
  const endDate = parseAsKst(endAtStr);

  if (!startDate || !endDate) {
    return { date: "-", day: "-", timeRange: "-", hours: 0 };
  }

  const formatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  });

  const parts = formatter.formatToParts(startDate);
  const year = parts.find((p) => p.type === "year")?.value || "";
  const month = parts.find((p) => p.type === "month")?.value || "";
  const dayNum = parts.find((p) => p.type === "day")?.value || "";
  const dayName = parts.find((p) => p.type === "weekday")?.value || "";

  const startTime = formatKstTime(startDate);
  const endTime = formatKstTime(endDate);

  const workHours = getWorkHours(startTime, endTime);

  return {
    date: `${year}.${month}.${dayNum}`,
    day: dayName,
    timeRange: `${startTime} - ${endTime}`,
    hours: workHours,
  };
}

// 근무 시간 계산 (자정 넘어가는 밤샘 근무 예외 처리 보장)
function getWorkHours(startTime: string, endTime: string): number {
  const [startHour, startMinute] = startTime.split(":").map(Number);
  const [endHour, endMinute] = endTime.split(":").map(Number);

  const startMins = startHour * 60 + startMinute;
  let endMins = endHour * 60 + endMinute;

  if (endMins < startMins) {
    endMins += 24 * 60;
  }

  return (endMins - startMins) / 60;
}

// 이름 첫 글자 추출
function getInitial(name: string | null | undefined) {
  return name ? name.trim().slice(0, 1) : "";
}

// 끝난 시각 (MM.DD (요일) HH:mm). closedAt의 소수점 초는 떼고 읽음
function formatClosedAt(isoString: string): string {
  const closedDate = parseAsKst(isoString.split(".")[0]);
  if (!closedDate) return "-";

  const parts = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  }).formatToParts(closedDate);
  const month = parts.find((p) => p.type === "month")?.value || "";
  const dayNum = parts.find((p) => p.type === "day")?.value || "";
  const dayName = parts.find((p) => p.type === "weekday")?.value || "";

  return `${month}.${dayNum} (${dayName}) ${formatKstTime(closedDate)}`;
}

// 끝난 요청의 결과 문구 (FRONTEND-CONTRACT §3-4 행 표시)
function getClosedResultView(request: ClosedSubstituteRequestItem): ClosedResultView {
  switch (request.closeReason) {
    case "ALL_CANDIDATES_REJECTED":
      return { label: "전원 거절", badgeClassName: RED_BADGE };
    case "CANDIDATE_INELIGIBLE":
      return { label: "후보 자격 상실", badgeClassName: RED_BADGE };
    case "SHIFT_CHANGED":
      return { label: "근무 변경으로 종료", badgeClassName: GRAY_BADGE };
    case "MANAGER_CLOSED":
      return { label: "관리자 종료", badgeClassName: GRAY_BADGE };
    case "EXPIRED":
      return { label: "승인 전에 근무 시작", badgeClassName: GRAY_BADGE };
    default:
      return { label: "종료됨", badgeClassName: GRAY_BADGE };
  }
}

// 조치 필요: 전원 거절·후보 자격 상실로 끝났고, 그 근무를 아직 요청자가 맡고 있고, 시작 전이고, 새 요청도 없음 (§3-4, 취소·시작한 근무 제외)
function needsAction(request: ClosedSubstituteRequestItem): boolean {
  return (
      request.outcome === "CLOSED" &&
      (request.closeReason === "ALL_CANDIDATES_REJECTED" ||
          request.closeReason === "CANDIDATE_INELIGIBLE") &&
      request.shiftStatus === "SCHEDULED" &&
      request.requesterStillAssigned &&
      !request.shiftStarted &&
      !request.hasNewerActiveRequest
  );
}

// 확인 필요: 수락자가 있었는데 승인 전에 근무가 시작됨 (안내만, 시스템 안 조치 없음)
function needsCheck(request: ClosedSubstituteRequestItem): boolean {
  return request.outcome === "EXPIRED" && request.accepterName !== null;
}

export default function SubstituteAdminPage() {
  const routeParams = useParams();
  const workplaceId = routeParams?.workplaceId as string;

  const [requests, setRequests] = useState<SubstituteRequestItem[]>([]);
  const [tab, setTab] = useState<Tab>("WAITING");
  const [selectedRequest, setSelectedRequest] =
      useState<SubstituteRequestItem | null>(null);
  const [isLoading, setIsLoading] = useState(() => Boolean(workplaceId));
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 재시도 트리거용 상태
  const [retryCount, setRetryCount] = useState(0);

  // 끝난 요청은 따로 불러서, 실패해도 다른 탭은 그대로 보이게 함 (기본 최근 7일, 페이지 단위)
  const [closedRequests, setClosedRequests] = useState<ClosedSubstituteRequestItem[]>([]);
  const [closedPage, setClosedPage] = useState(0);
  const [closedHasNext, setClosedHasNext] = useState(false);
  const [isClosedLoading, setIsClosedLoading] = useState(() => Boolean(workplaceId));
  const [closedLoadError, setClosedLoadError] = useState<string | null>(null);
  const [closedReloadCount, setClosedReloadCount] = useState(0);

  useEffect(() => {
    if (!workplaceId) return;

    let isIgnore = false;

    // 비동기 함수 선언
    const loadRequests = async () => {
      setIsLoading(true);
      setLoadError(null);
      setRequests([]);

      try {
        const data = await apiFetch<SubstituteRequestItem[]>(
            `/workplaces/${workplaceId}/substitute-requests/ongoing`
        );
        if (!isIgnore) {
          setRequests(data ?? []);
        }
      } catch (error) {
        if (!isIgnore) {
          setRequests([]);
          if (error instanceof ApiError) {
            setLoadError(`[${error.code}] ${error.message}`);
          } else {
            setLoadError(
                "대체 근무 요청 목록을 불러오지 못했습니다. 네트워크 상태를 확인해주세요."
            );
          }
        }
      } finally {
        if (!isIgnore) {
          setIsLoading(false);
        }
      }
    };

    loadRequests();
    return () => {
      isIgnore = true;
    }
  }, [workplaceId, retryCount]);

  useEffect(() => {
    if (!workplaceId) return;

    let isIgnore = false;

    const loadClosedRequests = async () => {
      setIsClosedLoading(true);
      setClosedLoadError(null);

      try {
        const data = await apiFetch<ClosedSubstituteRequestPage>(
            `/workplaces/${workplaceId}/substitute-requests/closed?page=0`
        );
        if (!isIgnore) {
          setClosedRequests(data.items);
          setClosedPage(0);
          setClosedHasNext(data.hasNext);
        }
      } catch (error) {
        if (!isIgnore) {
          setClosedRequests([]);
          setClosedHasNext(false);
          if (error instanceof ApiError) {
            setClosedLoadError(`[${error.code}] ${error.message}`);
          } else {
            setClosedLoadError("끝난 요청 목록을 불러오지 못했습니다. 네트워크 상태를 확인해주세요.");
          }
        }
      } finally {
        if (!isIgnore) {
          setIsClosedLoading(false);
        }
      }
    };

    loadClosedRequests();
    return () => {
      isIgnore = true;
    };
  }, [workplaceId, closedReloadCount]);

  // 끝난 요청 다음 페이지
  const handleLoadMoreClosed = async () => {
    if (isClosedLoading) return;
    setIsClosedLoading(true);

    try {
      const nextPage = closedPage + 1;
      const data = await apiFetch<ClosedSubstituteRequestPage>(
          `/workplaces/${workplaceId}/substitute-requests/closed?page=${nextPage}`
      );
      setClosedRequests((prev) => [...prev, ...data.items]);
      setClosedPage(nextPage);
      setClosedHasNext(data.hasNext);
    } catch (error) {
      if (error instanceof ApiError) {
        alert(`[${error.code}] ${error.message}`);
      } else {
        alert("끝난 요청을 더 불러오지 못했습니다.");
      }
    } finally {
      setIsClosedLoading(false);
    }
  };

  // Esc 키 모달 닫기
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && selectedRequest && !isSubmitting) {
        setSelectedRequest(null);
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [selectedRequest, isSubmitting]);

  // 탭 변경 함수
  const handleTabChange = useCallback(
      (newTab: Tab) => {
        if (isSubmitting) return;
        setTab(newTab);
        setSelectedRequest(null);
      },
      [isSubmitting]
  );

  // 최종 승인 처리
  const handleApprove = useCallback(async (requestId: number) => {
    if (isSubmitting) return;
    setIsSubmitting(true);

    try {
      await apiFetch(`/substitute-requests/${requestId}/approve`, {
        method: "PATCH",
      });

      setRequests((prev) =>
          prev.filter((req) =>
              req.requestId !== requestId));
      setSelectedRequest(null);
      alert("대체 근무 요청이 최종 승인되었습니다.");
    } catch (error) {
      if (error instanceof ApiError) {
        alert(`[${error.code}] ${error.message}`);
      } else {
        alert("승인 처리 중 오류가 발생했습니다.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }, [isSubmitting]);

  // 요청 종료 처리
  const handleClose = useCallback(async (requestId: number) => {
    if (isSubmitting) return;
    setIsSubmitting(true);

    try {
      await apiFetch(`/substitute-requests/${requestId}/close`, {
        method: "PATCH",
      });

      setRequests((prev) =>
          prev.filter((req) =>
              req.requestId !== requestId));
      setSelectedRequest(null);
      setClosedReloadCount((prev) => prev + 1);
      alert("요청이 종료되었습니다.");
    } catch (error) {
      if (error instanceof ApiError) {
        alert(`[${error.code}] ${error.message}`);
      } else {
        alert("종료 처리 중 오류가 발생했습니다.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }, [isSubmitting]);

  // 탭 카운트
  const waitingCount = requests.filter((r) => r.status === "OPEN").length;
  const approvalCount = requests.filter((r) => r.status === "ACCEPTED").length;

  // 탭 필터링
  const filteredRequests = requests.filter((request) => {
    if (tab === "WAITING") return request.status === "OPEN";
    if (tab === "APPROVAL") return request.status === "ACCEPTED";
    return false;
  });

  return (
      <>
        <PageHeader
            title="승인 관리"
            description="직원이 수락한 대체 근무 요청을 확인하고 최종 승인하세요."
        />

        {/* 탭 필터 버튼 */}
        <div className="mb-5 flex flex-wrap gap-2">
          <button
              type="button"
              onClick={() => handleTabChange("WAITING")}
              disabled={isSubmitting}
              className={`rounded-full px-4 py-2 text-sm font-bold transition disabled:opacity-50 ${
                  tab === "WAITING"
                      ? "bg-[#005642] text-white"
                      : "text-[#66736d] hover:bg-[#f3fbf7]"
              }`}
          >
            수락 대기 {waitingCount}
          </button>

          <button
              type="button"
              onClick={() => handleTabChange("APPROVAL")}
              disabled={isSubmitting}
              className={`rounded-full px-4 py-2 text-sm font-bold transition disabled:opacity-50 ${
                  tab === "APPROVAL"
                      ? "bg-[#005642] text-white"
                      : "text-[#66736d] hover:bg-[#f3fbf7]"
              }`}
          >
            승인 필요 {approvalCount}
          </button>

          <button
              type="button"
              onClick={() => {
                handleTabChange("CLOSED");
                // 탭에 들어올 때마다 다시 불러옴 (화면을 열어 둔 사이 끝나거나 만료된 요청 반영)
                setClosedReloadCount((prev) => prev + 1);
              }}
              disabled={isSubmitting}
              className={`rounded-full px-4 py-2 text-sm font-bold transition disabled:opacity-50 ${
                  tab === "CLOSED"
                      ? "bg-[#005642] text-white"
                      : "text-[#66736d] hover:bg-[#f3fbf7]"
              }`}
          >
            끝난 요청
          </button>
        </div>

        <Card>
          <div className="overflow-x-auto">
            <div className="min-w-[850px]">
              <div className="grid grid-cols-[1.25fr_1fr_1fr_1fr_1fr] border-b border-[#edf2ef] px-3 pb-3 text-sm font-bold text-[#78847f]">
                <div>근무 일시</div>
                <div>요청자</div>
                <div>수락자</div>
                <div>{tab === "CLOSED" ? "결과" : "상태"}</div>
                <div className="text-right">{tab === "CLOSED" ? "안내" : "관리"}</div>
              </div>

              {/* 끝난 요청 탭: 행에서 승인·종료하지 않고 거절 후보도 보여 주지 않음 (§3-4) */}
              {tab === "CLOSED" ? (
                  closedRequests.length === 0 && isClosedLoading ? (
                      <div className="flex min-h-[200px] items-center justify-center text-sm text-[#78847f]">
                        끝난 요청을 불러오는 중입니다...
                      </div>
                  ) : closedLoadError ? (
                      <div className="flex min-h-[200px] flex-col items-center justify-center gap-3 text-sm text-[#d95555]">
                        <p>{closedLoadError}</p>
                        <button
                            type="button"
                            onClick={() => setClosedReloadCount((prev) => prev + 1)}
                            className="rounded-lg border border-[#f1cccc] bg-white px-4 py-2 text-xs font-bold text-[#d95555] hover:bg-[#fff5f5]"
                        >
                          다시 시도
                        </button>
                      </div>
                  ) : closedRequests.length === 0 ? (
                      <div className="flex min-h-[200px] items-center justify-center text-sm text-[#78847f]">
                        최근 7일 동안 끝난 요청이 없습니다.
                      </div>
                  ) : (
                      <>
                        {closedRequests.map((request) => {
                          const { date, day, timeRange, hours } = formatShiftDateTime(
                              request.startAt,
                              request.endAt
                          );
                          const resultView = getClosedResultView(request);

                          return (
                              <div
                                  key={request.requestId}
                                  className="grid min-h-[88px] grid-cols-[1.25fr_1fr_1fr_1fr_1fr] items-center border-b border-[#edf2ef] px-3 last:border-b-0"
                              >
                                <div>
                                  <p className="font-black">
                                    {date} ({day})
                                  </p>
                                  <p className="mt-1 text-sm text-[#78847f]">
                                    {timeRange}{" "}
                                    <span className="text-xs text-[#9aa5a0]">({hours}h)</span>
                                  </p>
                                </div>

                                {/* 요청 당시 소속이 끝났으면 "(이전 소속)". 재가입했어도 퇴사로 단정하지 않음 */}
                                <div className="flex items-center gap-3">
                                  <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[#f3fbf7] font-black text-[#005642]">
                                    {getInitial(request.requesterName)}
                                  </div>
                                  <p className="font-bold">
                                    {request.requesterName}
                                    {request.requesterMembershipEnded && (
                                        <span className="ml-1 text-xs font-semibold text-[#9aa5a0]">(이전 소속)</span>
                                    )}
                                  </p>
                                </div>

                                <div>
                                  {request.accepterName ? (
                                      <div className="flex items-center gap-3">
                                        <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[#dff7ec] font-black text-[#005642]">
                                          {getInitial(request.accepterName)}
                                        </div>
                                        <p className="font-bold">{request.accepterName}</p>
                                      </div>
                                  ) : (
                                      <p className="text-sm text-[#9aa5a0]">없음</p>
                                  )}
                                </div>

                                <div>
                                  <span className={`rounded-full px-3 py-1 text-xs font-bold ${resultView.badgeClassName}`}>
                                    {resultView.label}
                                  </span>
                                  <p className="mt-2 text-xs text-[#9aa5a0]">
                                    {request.outcome === "EXPIRED"
                                        ? `만료 시각 ${formatClosedAt(request.closedAt)}`
                                        : `${formatClosedAt(request.closedAt)} 종료`}
                                  </p>
                                </div>

                                {/* 표시는 안내일 뿐. 바로가기로 실행하는 담당자 변경은 서버가 그 시점에 다시 검증함 */}
                                <div className="text-right">
                                  {needsAction(request) ? (
                                      <>
                                        <span className="rounded-full bg-[#fff1d7] px-3 py-1 text-xs font-bold text-[#a96d09]">
                                          조치 필요
                                        </span>
                                        <p className="mt-2 text-xs text-[#66736d]">다른 직원 배정을 검토하세요</p>
                                        <Link
                                            href={`/workplaces/${workplaceId}/schedule`}
                                            className="mt-1 inline-block text-xs font-bold text-[#005642] underline"
                                        >
                                          근무표에서 담당자 변경
                                        </Link>
                                      </>
                                  ) : needsCheck(request) ? (
                                      <>
                                        <span className="rounded-full bg-[#e7efff] px-3 py-1 text-xs font-bold text-[#4a73c9]">
                                          확인 필요
                                        </span>
                                        <p className="mt-2 text-xs text-[#66736d]">
                                          승인 전에 근무가 시작됨 — 실제 근무자를 확인하세요
                                        </p>
                                      </>
                                  ) : (
                                      <p className="text-sm text-[#9aa5a0]">-</p>
                                  )}
                                </div>
                              </div>
                          );
                        })}

                        {closedHasNext && (
                            <div className="flex justify-center pt-4">
                              <button
                                  type="button"
                                  onClick={handleLoadMoreClosed}
                                  disabled={isClosedLoading}
                                  className="rounded-lg border border-[#dce8e2] px-4 py-2 text-sm font-bold text-[#66736d] transition hover:bg-[#f3fbf7] disabled:cursor-not-allowed disabled:opacity-50"
                              >
                                {isClosedLoading ? "불러오는 중..." : "더 보기"}
                              </button>
                            </div>
                        )}
                      </>
                  )
              ) : isLoading ? (
                  /* 1. 로딩 중 */
                  <div className="flex min-h-[200px] items-center justify-center text-sm text-[#78847f]">
                    요청 목록을 불러오는 중입니다...
                  </div>
              ) : loadError ? (
                  /* 2. 조회 오류 발생 시 */
                  <div className="flex min-h-[200px] flex-col items-center justify-center gap-3 text-sm text-[#d95555]">
                    <p>{loadError}</p>
                    <button
                        type="button"
                        onClick={() => setRetryCount((prev) => prev + 1)}
                        className="rounded-lg border border-[#f1cccc] bg-white px-4 py-2 text-xs font-bold text-[#d95555] hover:bg-[#fff5f5]"
                    >
                      다시 시도
                    </button>
                  </div>
              ) : filteredRequests.length === 0 ? (
                  /* 3. 정상 빈 목록 */
                  <div className="flex min-h-[200px] items-center justify-center text-sm text-[#78847f]">
                    {tab === "WAITING" && "현재 수락을 기다리는 요청이 없습니다."}
                    {tab === "APPROVAL" && "현재 승인이 필요한 요청이 없습니다."}
                  </div>
              ) : (
                  /* 4. 정상 목록 출력 */
                  filteredRequests.map((request) => {
                    const { date, day, timeRange, hours } = formatShiftDateTime(
                        request.startAt,
                        request.endAt
                    );

                    return (
                        <div
                            key={request.requestId}
                            className="grid min-h-[88px] grid-cols-[1.25fr_1fr_1fr_1fr_1fr] items-center border-b border-[#edf2ef] px-3 last:border-b-0"
                        >
                          <div>
                            <p className="font-black">
                              {date} ({day})
                            </p>
                            <p className="mt-1 text-sm text-[#78847f]">
                              {timeRange}{" "}
                              <span className="text-xs text-[#9aa5a0]">
                          ({hours}h)
                        </span>
                            </p>
                          </div>

                          <div className="flex items-center gap-3">
                            <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[#f3fbf7] font-black text-[#005642]">
                              {getInitial(request.requesterName)}
                            </div>
                            <p className="font-bold">{request.requesterName}</p>
                          </div>

                          <div>
                            {request.acceptedMember ? (
                                <div className="flex items-center gap-3">
                                  <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-[#dff7ec] font-black text-[#005642]">
                                    {getInitial(request.acceptedMember.name)}
                                  </div>
                                  <p className="font-bold">
                                    {request.acceptedMember.name}
                                  </p>
                                </div>
                            ) : (
                                <p className="text-sm text-[#9aa5a0]">아직 없음</p>
                            )}
                          </div>

                          <div>
                            {request.status === "OPEN" && (
                                <span className="rounded-full bg-[#e7efff] px-3 py-1 text-xs font-bold text-[#4a73c9]">
                          수락 대기
                        </span>
                            )}
                            {request.status === "ACCEPTED" && (
                                <span className="rounded-full bg-[#fff1d7] px-3 py-1 text-xs font-bold text-[#a96d09]">
                          승인 필요
                        </span>
                            )}
                          </div>

                          <div className="flex justify-end gap-2">
                            {request.status === "ACCEPTED" && (
                                <>
                                  <button
                                      type="button"
                                      onClick={() => handleClose(request.requestId)}
                                      disabled={isSubmitting}
                                      className="rounded-lg border border-[#f1cccc] px-3 py-2 text-sm font-bold text-[#d95555] transition hover:bg-[#fff5f5] disabled:cursor-not-allowed disabled:opacity-50"
                                  >
                                    {isSubmitting ? "처리 중..." : "종료"}
                                  </button>
                                  <button
                                      type="button"
                                      onClick={() => handleApprove(request.requestId)}
                                      disabled={isSubmitting}
                                      className="rounded-lg bg-[#005642] px-3 py-2 text-sm font-bold text-white transition hover:bg-[#0b6b52] disabled:cursor-not-allowed disabled:opacity-50"
                                  >
                                    {isSubmitting ? "처리 중..." : "승인"}
                                  </button>
                                </>
                            )}

                            {request.status === "OPEN" && (
                                <button
                                    type="button"
                                    onClick={() => setSelectedRequest(request)}
                                    disabled={isSubmitting}
                                    className="rounded-lg border border-[#dce8e2] px-3 py-2 text-sm font-bold text-[#66736d] transition hover:bg-[#f3fbf7] disabled:cursor-not-allowed disabled:opacity-50"
                                >
                                  상세 보기
                                </button>
                            )}
                          </div>
                        </div>
                    );
                  })
              )}
            </div>
          </div>
        </Card>

        <div className="mt-5 rounded-xl border border-[#dce8e2] bg-[#f3fbf7] p-4">
          <p className="text-sm font-black text-[#005642]">최종 승인 안내</p>
          <p className="mt-1 text-sm leading-6 text-[#66736d]">
            관리자가 승인하면 해당 스케줄의 근무 담당자가 대타를 수락한 직원으로
            자동으로 교체됩니다.
          </p>
        </div>

        {/* 상세 보기 모달 */}
        {selectedRequest &&
            (() => {
              const { date, day, timeRange, hours } = formatShiftDateTime(
                  selectedRequest.startAt,
                  selectedRequest.endAt
              );

              return (
                  <div
                      className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 px-4"
                      onClick={() => !isSubmitting && setSelectedRequest(null)}
                  >
                    <div
                        className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl"
                        onClick={(e) => e.stopPropagation()}
                    >
                      <div className="flex items-start justify-between">
                        <div>
                          <h2 className="text-xl font-black">대체 근무 요청 상세</h2>
                          <p className="mt-1 text-sm text-[#78847f]">
                            요청 정보 및 진행 상태를 확인합니다.
                          </p>
                        </div>

                        <button
                            type="button"
                            onClick={() => setSelectedRequest(null)}
                            disabled={isSubmitting}
                            className="text-xl text-[#78847f] hover:text-black disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          ×
                        </button>
                      </div>

                      <div className="mt-6 space-y-4">
                        <div className="rounded-xl bg-[#f3fbf7] p-4">
                          <p className="text-xs font-semibold text-[#78847f]">
                            근무 일시
                          </p>
                          <p className="mt-1 font-black">
                            {date} ({day})
                          </p>
                          <p className="mt-1 text-sm text-[#66736d]">
                            {timeRange} ({hours}시간)
                          </p>
                        </div>

                        <div className="grid grid-cols-2 gap-3">
                          <div className="rounded-xl border border-[#dce8e2] p-4">
                            <p className="text-xs text-[#78847f]">요청자</p>
                            <p className="mt-1 font-black">
                              {selectedRequest.requesterName}
                            </p>
                          </div>

                          <div className="rounded-xl border border-[#dce8e2] p-4">
                            <p className="text-xs text-[#78847f]">수락자</p>
                            <p className="mt-1 font-black">
                              {selectedRequest.acceptedMember?.name ?? "아직 없음"}
                            </p>
                          </div>
                        </div>

                        <div>
                          <p className="mb-2 text-sm font-bold">현재 상태</p>
                          {selectedRequest.status === "OPEN" && (
                              <div className="rounded-xl bg-[#eef4ff] px-4 py-3 text-sm font-bold text-[#4a73c9]">
                                후보 직원의 수락 응답을 기다리고 있습니다.
                              </div>
                          )}
                          {selectedRequest.status === "ACCEPTED" && (
                              <div className="rounded-xl bg-[#fff8e8] px-4 py-3 text-sm font-bold text-[#a96d09]">
                                후보가 수락했습니다. 관리자의 최종 승인이 필요합니다.
                              </div>
                          )}
                        </div>

                        {/* 처리 중일 때 안내 문구 */}
                        {isSubmitting && (
                            <p className="animate-pulse text-center text-xs font-bold text-[#005642]">
                              요청을 처리하고 있습니다. 잠시만 기다려 주세요.
                            </p>
                        )}
                      </div>

                      <div className="mt-6 flex gap-3">
                        <button
                            type="button"
                            onClick={() => setSelectedRequest(null)}
                            disabled={isSubmitting}
                            className="flex-1 rounded-xl border border-[#dce8e2] px-4 py-3 text-sm font-bold text-[#66736d] transition hover:bg-[#f3fbf7] disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          닫기
                        </button>

                        {selectedRequest.status === "OPEN" && (
                            <button
                                type="button"
                                onClick={() => handleClose(selectedRequest.requestId)}
                                disabled={isSubmitting}
                                className="flex-1 rounded-xl border border-[#f1cccc] px-4 py-3 text-sm font-bold text-[#d95555] transition hover:bg-[#fff5f5] disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              {isSubmitting ? "종료 중..." : "요청 종료"}
                            </button>
                        )}

                        {selectedRequest.status === "ACCEPTED" && (
                            <button
                                type="button"
                                onClick={() => handleApprove(selectedRequest.requestId)}
                                disabled={isSubmitting}
                                className="flex-1 rounded-xl bg-[#005642] px-4 py-3 text-sm font-bold text-white transition hover:bg-[#0b6b52] disabled:cursor-not-allowed disabled:opacity-50"
                            >
                              {isSubmitting ? "승인 중..." : "최종 승인"}
                            </button>
                        )}
                      </div>
                    </div>
                  </div>
              );
            })()}
      </>
  );
}