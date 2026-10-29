"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import PageHeader from "@/components/ui/PageHeader";
import { apiFetch } from "@/lib/api";

type RequestStatus = "PENDING" | "ACCEPTED";

type SubstituteRequest = {
  id: number;
  requesterName: string;
  workplaceName: string;
  date: string;
  day: string;
  startTime: string;
  endTime: string;
  status: RequestStatus;
};

type ReceivedSubstituteRequestResponse = {
  candidateId: number;
  requestId: number;
  shiftId: number;
  workplaceId: number;
  workplaceName: string;
  requesterMemberId: number;
  requesterName: string;
  startAt: string;
  endAt: string;
  requestStatus: "OPEN";
  candidateStatus: "PENDING";
  createdAt: string;
};

type AcceptedSubstituteRequestResponse = {
  candidateId: number;
  requestId: number;
  shiftId: number;
  workplaceId: number;
  workplaceName: string;
  requesterName: string;
  startAt: string;
  endAt: string;
  requestStatus: "ACCEPTED";
  candidateStatus: "ACCEPTED";
  respondedAt: string;
};

// 내가 수락했고 끝난 요청 (API-SB-01). 만료는 CLOSED / EXPIRED, closedAt = 효력 종료 시각
type AcceptedClosedSubstituteRequestResponse = {
  requestId: number;
  workplaceId: number;
  workplaceName: string;
  requesterName: string;
  startAt: string;
  endAt: string;
  requestStatus: "APPROVED" | "CLOSED";
  closeReason:
      | "ALL_CANDIDATES_REJECTED"
      | "EXPIRED"
      | "MANAGER_CLOSED"
      | "SHIFT_CHANGED"
      | "CANDIDATE_INELIGIBLE"
      | null;
  candidateStatus: "ACCEPTED";
  closedAt: string;
};

type ClosedView = {
  label: string;
  description: string;
  badgeClassName: string;
};

type Tab = "PENDING" | "ACCEPTED" | "PAST";

const GREEN_BADGE = "bg-[#dff7ec] text-[#14956c]";
const GRAY_BADGE = "bg-[#f3f5f4] text-[#66736d]";

const WEEKDAY_LABELS = ["일", "월", "화", "수", "목", "금", "토"];
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

// 서버 시간 문자열을 한국 시간으로 읽음 (소수점 초는 떼고 읽음)
function parseAsKst(iso: string): Date {
  return new Date(`${iso.split(".")[0]}+09:00`);
}

function toKstDisplay(iso: string): Date {
  return new Date(parseAsKst(iso).getTime() + KST_OFFSET_MS);
}

function formatDateLabel(iso: string): string {
  const date = toKstDisplay(iso);
  return `${date.getUTCMonth() + 1}월 ${date.getUTCDate()}일`;
}

function formatDayLabel(iso: string): string {
  return WEEKDAY_LABELS[toKstDisplay(iso).getUTCDay()];
}

function formatTimeLabel(iso: string): string {
  const date = toKstDisplay(iso);
  const hour = String(date.getUTCHours()).padStart(2, "0");
  const minute = String(date.getUTCMinutes()).padStart(2, "0");

  return `${hour}:${minute}`;
}

// 끝난 요청의 배지와 문구 (FRONTEND-CONTRACT §4-2). "승인됨"은 지금 담당자를 뜻하지 않음 (§7)
function getClosedView(request: AcceptedClosedSubstituteRequestResponse): ClosedView {
  if (request.requestStatus === "APPROVED") {
    return {
      label: "승인됨",
      description: "승인됨 — 현재 근무는 예정 근무에서 확인하세요",
      badgeClassName: GREEN_BADGE,
    };
  }

  if (request.closeReason === "MANAGER_CLOSED") {
    return {
      label: "관리자 종료",
      description: "관리자가 요청을 종료했어요",
      badgeClassName: GRAY_BADGE,
    };
  }

  if (request.closeReason === "EXPIRED") {
    return {
      label: "기간 만료",
      description: "근무 시작 시각에 만료되었어요",
      badgeClassName: GRAY_BADGE,
    };
  }

  if (request.closeReason === "SHIFT_CHANGED") {
    return {
      label: "근무 변경",
      description: "근무가 변경되어 요청이 종료되었어요",
      badgeClassName: GRAY_BADGE,
    };
  }

  // 예상 못 한 값이 오면 기본값
  return {
    label: "종료됨",
    description: "종료된 요청이에요",
    badgeClassName: GRAY_BADGE,
  };
}

// 승인·종료 날짜 한 줄. 만료는 "종료일"이 아니라 "만료 시각"으로 (G03-D-19 D19-5와 같은 표현)
function getClosedDateLine(request: AcceptedClosedSubstituteRequestResponse): string {
  if (request.requestStatus === "APPROVED") {
    return `${formatDateLabel(request.closedAt)} 승인`;
  }

  if (request.closeReason === "EXPIRED") {
    return `만료 시각 ${formatDateLabel(request.closedAt)} (${formatDayLabel(request.closedAt)}) ${formatTimeLabel(request.closedAt)}`;
  }

  return `${formatDateLabel(request.closedAt)} 종료`;
}

export default function ReceivedRequestsPage() {
  const params = useParams();
  const workplaceId = Number(params.workplaceId);

  const [requests, setRequests] = useState<SubstituteRequest[]>([]);
  const [tab, setTab] = useState<Tab>("PENDING");
  const [isResponding, setIsResponding] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  // 끝난 요청은 따로 불러서, 실패해도 다른 탭은 그대로 보이게 함
  const [closedRequests, setClosedRequests] = useState<AcceptedClosedSubstituteRequestResponse[]>([]);
  const [isClosedLoading, setIsClosedLoading] = useState(true);
  const [closedLoadError, setClosedLoadError] = useState<string | null>(null);
  // 지난 요청 탭에 들어올 때·다시 시도할 때 다시 불러옴 (화면을 열어 둔 사이 끝난 요청 반영)
  const [closedReloadCount, setClosedReloadCount] = useState(0);

  useEffect(() => {
    let ignore = false;

    const fetchRequests = async () => {
      try {
        setIsLoading(true);
        setLoadError(null);

        const [receivedData, acceptedData] = await Promise.all([
          apiFetch<ReceivedSubstituteRequestResponse[]>(
              "/substitute-requests/received"
          ),
          apiFetch<AcceptedSubstituteRequestResponse[]>(
              "/substitute-requests/accepted"
          ),
        ]);

        const receivedRequests: SubstituteRequest[] = receivedData
        .filter((request) => request.workplaceId === workplaceId)
        .map((request) => {

          return {
            id: request.candidateId,
            requesterName: request.requesterName,
            workplaceName: request.workplaceName,
            date: formatDateLabel(request.startAt),
            day: formatDayLabel(request.startAt),
            startTime: formatTimeLabel(request.startAt),
            endTime: formatTimeLabel(request.endAt),
            status: "PENDING",
          };
        });

        const acceptedRequests: SubstituteRequest[] = acceptedData
        .filter((request) => request.workplaceId === workplaceId)
        .map((request) => {

          return {
            id: request.candidateId,
            requesterName: request.requesterName,
            workplaceName: request.workplaceName,
            date: formatDateLabel(request.startAt),
            day: formatDayLabel(request.startAt),
            startTime: formatTimeLabel(request.startAt),
            endTime: formatTimeLabel(request.endAt),
            status: "ACCEPTED",
          };
        });

        if (ignore) return;

        setRequests([...receivedRequests, ...acceptedRequests]);
      } catch (error) {
        if (ignore) return;

        setLoadError(
            error instanceof Error
                ? error.message
                : "대타 요청을 불러오지 못했습니다."
        );
      } finally {
        if (!ignore) {
          setIsLoading(false);
        }
      }
    };

    fetchRequests();

    return () => {
      ignore = true;
    };
  }, [workplaceId]);

  useEffect(() => {
    let ignore = false;

    const fetchClosedRequests = async () => {
      try {
        setIsClosedLoading(true);
        setClosedLoadError(null);

        const closedData = await apiFetch<AcceptedClosedSubstituteRequestResponse[]>(
            "/substitute-requests/accepted/closed"
        );

        if (ignore) return;

        // 지금 근무지 요청만, 서버 순서(최근에 끝난 순) 그대로
        setClosedRequests(
            closedData.filter((request) => request.workplaceId === workplaceId)
        );
      } catch (error) {
        if (ignore) return;

        setClosedLoadError(
            error instanceof Error
                ? error.message
                : "지난 요청을 불러오지 못했습니다."
        );
      } finally {
        if (!ignore) {
          setIsClosedLoading(false);
        }
      }
    };

    fetchClosedRequests();

    return () => {
      ignore = true;
    };
  }, [workplaceId, closedReloadCount]);

  const handleAccept = async (candidateId: number) => {
    try {
      setIsResponding(true);

      await apiFetch(
          `/substitute-candidates/${candidateId}/response`,
          {
            method: "PATCH",
            body: JSON.stringify({
              decision: "ACCEPT",
            }),
          }
      );

      setRequests((prevRequests) =>
          prevRequests.map((request) =>
              request.id === candidateId
                  ? { ...request, status: "ACCEPTED" }
                  : request
          )
      );
    } catch (error) {
      const message =
          error instanceof Error
              ? error.message
              : "대타 요청 수락에 실패했습니다.";

      alert(message);
    } finally {
      setIsResponding(false);
    }
  };

  const handleReject = async (candidateId: number) => {
    try {
      setIsResponding(true);

      await apiFetch(
          `/substitute-candidates/${candidateId}/response`,
          {
            method: "PATCH",
            body: JSON.stringify({
              decision: "REJECT",
            }),
          }
      );

      setRequests((prevRequests) =>
          prevRequests.filter((request) => request.id !== candidateId)
      );
    } catch (error) {
      const message =
          error instanceof Error
              ? error.message
              : "대타 요청 거절에 실패했습니다.";

      alert(message);
    } finally {
      setIsResponding(false);
    }
  };

  const filteredRequests = requests.filter(
      (request) => request.status === tab
  );

  const pendingCount = requests.filter(
      (request) => request.status === "PENDING"
  ).length;

  const acceptedCount = requests.filter(
      (request) => request.status === "ACCEPTED"
  ).length;

  return (
      <>
        <PageHeader
            title="받은 요청"
            description="나에게 도착한 대체 근무 요청을 확인하고 응답하세요."
        />

        {/* 탭 */}
        <div className="mb-5 flex flex-wrap gap-2">
          <button
              type="button"
              onClick={() => setTab("PENDING")}
              className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                  tab === "PENDING"
                      ? "bg-[#005642] text-white"
                      : "text-[#66736d] hover:bg-[#f3fbf7]"
              }`}
          >
            대기 중 {pendingCount}
          </button>

          <button
              type="button"
              onClick={() => setTab("ACCEPTED")}
              className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                  tab === "ACCEPTED"
                      ? "bg-[#005642] text-white"
                      : "text-[#66736d] hover:bg-[#f3fbf7]"
              }`}
          >
            승인 대기 {acceptedCount}
          </button>

          <button
              type="button"
              onClick={() => {
                setTab("PAST");
                setClosedReloadCount((prev) => prev + 1);
              }}
              className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                  tab === "PAST"
                      ? "bg-[#005642] text-white"
                      : "text-[#66736d] hover:bg-[#f3fbf7]"
              }`}
          >
            지난 요청 {closedRequests.length}
          </button>
        </div>

        {/* 요청 목록 */}
        <div className="space-y-4">

          {tab !== "PAST" && isLoading && (
              <div className="flex min-h-[240px] items-center justify-center rounded-2xl border border-dashed border-[#dce8e2] bg-white text-sm text-[#78847f]">
                대타 요청을 불러오는 중입니다...
              </div>
          )}

          {tab !== "PAST" && !isLoading && loadError && (
              <div className="flex min-h-[240px] items-center justify-center rounded-2xl border border-dashed border-[#f1cccc] bg-white text-sm text-[#d95555]">
                {loadError}
              </div>
          )}

          {tab !== "PAST" && !isLoading && !loadError && filteredRequests.map((request) => (
              <div
                  key={request.id}
                  className="rounded-2xl border border-[#dce8e2] bg-white p-5 shadow-sm"
              >
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <h2 className="text-lg font-black">
                      {request.date} ({request.day})
                    </h2>

                    <p className="mt-1 font-bold text-[#005642]">
                      {request.startTime} - {request.endTime}
                    </p>
                  </div>

                  {request.status === "PENDING" && (
                      <span className="rounded-full bg-[#fff1d7] px-3 py-1 text-xs font-bold text-[#a96d09]">
                        대기 중
                      </span>
                  )}

                  {request.status === "ACCEPTED" && (
                      <span className="rounded-full bg-[#dff7ec] px-3 py-1 text-xs font-bold text-[#14956c]">
                        승인 대기
                      </span>
                  )}
                </div>

                {/* 요청자 */}
                <div className="mt-5 rounded-xl bg-[#f3fbf7] p-4">
                  <p className="text-xs font-semibold text-[#78847f]">
                    요청자
                  </p>

                  <p className="mt-1 font-black">{request.requesterName}</p>

                  <p className="mt-1 text-sm text-[#78847f]">
                    {request.workplaceName}
                  </p>
                </div>

                {/* 대기 중일 때만 버튼 표시 */}
                {request.status === "PENDING" && (
                    <div className="mt-5 grid grid-cols-2 gap-3">
                      <button
                          type="button"
                          onClick={() => handleReject(request.id)}
                          disabled={isResponding}
                          className="rounded-xl border border-[#f1cccc] px-4 py-3 font-bold text-[#d95555] transition hover:bg-[#fff5f5] disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {isResponding ? "처리 중..." : "거절하기"}
                      </button>

                      <button
                          type="button"
                          onClick={() => handleAccept(request.id)}
                          disabled={isResponding}
                          className="rounded-xl bg-[#005642] px-4 py-3 font-bold text-white transition hover:bg-[#0b6b52] disabled:cursor-not-allowed disabled:opacity-50"
                      >
                        {isResponding ? "처리 중..." : "수락하기"}
                      </button>
                    </div>
                )}

                {request.status === "ACCEPTED" && (
                    <div className="mt-5 rounded-xl bg-[#f3fbf7] px-4 py-3 text-center text-sm font-bold text-[#14956c]">
                      대체 근무 요청을 수락했습니다. 관리자 최종 승인을 기다리고 있습니다.
                    </div>
                )}
              </div>
          ))}

          {/* 비어있는 탭 */}
          {tab !== "PAST" && !isLoading && !loadError && filteredRequests.length === 0 && (
              <div className="flex min-h-[240px] items-center justify-center rounded-2xl border border-dashed border-[#dce8e2] bg-white text-sm text-[#78847f]">
                {tab === "PENDING" && "현재 대기 중인 요청이 없습니다."}
                {tab === "ACCEPTED" && "승인 대기 중인 요청이 없습니다."}
              </div>
          )}

          {/* 지난 요청: 내가 수락했고 끝난 요청 (최근 7일). 지금 담당자·다른 후보는 보여 주지 않음 */}
          {tab === "PAST" && isClosedLoading && (
              <div className="flex min-h-[240px] items-center justify-center rounded-2xl border border-dashed border-[#dce8e2] bg-white text-sm text-[#78847f]">
                지난 요청을 불러오는 중입니다...
              </div>
          )}

          {tab === "PAST" && !isClosedLoading && closedLoadError && (
              <div className="flex min-h-[240px] flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-[#f1cccc] bg-white text-sm text-[#d95555]">
                <p>{closedLoadError}</p>
                <button
                    type="button"
                    onClick={() => setClosedReloadCount((prev) => prev + 1)}
                    className="rounded-lg border border-[#f1cccc] bg-white px-4 py-2 text-xs font-bold text-[#d95555] hover:bg-[#fff5f5]"
                >
                  다시 시도
                </button>
              </div>
          )}

          {tab === "PAST" && !isClosedLoading && !closedLoadError && closedRequests.map((request) => {
            const closedView = getClosedView(request);

            return (
                <div
                    key={request.requestId}
                    className="rounded-2xl border border-[#dce8e2] bg-white p-5 shadow-sm"
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <h2 className="text-lg font-black">
                        {formatDateLabel(request.startAt)} ({formatDayLabel(request.startAt)})
                      </h2>

                      <p className="mt-1 font-bold text-[#005642]">
                        {formatTimeLabel(request.startAt)} - {formatTimeLabel(request.endAt)}
                      </p>
                    </div>

                    <span className={`rounded-full px-3 py-1 text-xs font-bold ${closedView.badgeClassName}`}>
                      {closedView.label}
                    </span>
                  </div>

                  {/* 요청자 */}
                  <div className="mt-5 rounded-xl bg-[#f3fbf7] p-4">
                    <p className="text-xs font-semibold text-[#78847f]">
                      요청자
                    </p>

                    <p className="mt-1 font-black">{request.requesterName}</p>

                    <p className="mt-1 text-sm text-[#78847f]">
                      {request.workplaceName}
                    </p>
                  </div>

                  <div className="mt-5 rounded-xl bg-[#f3fbf7] px-4 py-3 text-center text-sm font-bold text-[#66736d]">
                    {closedView.description}
                  </div>

                  <p className="mt-3 text-xs text-[#9aa5a0]">
                    {getClosedDateLine(request)}
                  </p>
                </div>
            );
          })}

          {tab === "PAST" && !isClosedLoading && !closedLoadError && closedRequests.length === 0 && (
              <div className="flex min-h-[240px] items-center justify-center rounded-2xl border border-dashed border-[#dce8e2] bg-white text-sm text-[#78847f]">
                최근 7일 동안 끝난 요청이 없습니다.
              </div>
          )}
        </div>
      </>
  );
}