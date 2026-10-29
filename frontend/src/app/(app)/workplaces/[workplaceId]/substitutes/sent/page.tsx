"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import PageHeader from "@/components/ui/PageHeader";
import { ApiError, apiFetch, UnexpectedResponseError } from "@/lib/api";

type RequestStatus = "OPEN" | "ACCEPTED" | "APPROVED" | "CLOSED";
type CloseReason =
    | "ALL_CANDIDATES_REJECTED"
    | "EXPIRED"
    | "MANAGER_CLOSED"
    | "SHIFT_CHANGED"
    | "CANDIDATE_INELIGIBLE";

// 내가 보낸 요청 목록 응답
type SentSubstituteRequest = {
  requestId: number;
  shiftId: number;
  workplaceId: number;
  workplaceName: string;
  startAt: string;
  endAt: string;
  status: RequestStatus;
  closeReason: CloseReason | null;
  createdAt: string;
  approvedAt: string | null;
  closedAt: string | null;
};

type Tab = "ACTIVE" | "PAST";

type StatusView = {
  label: string;
  description: string;
  badgeClassName: string;
};

const YELLOW_BADGE = "bg-[#fff1d7] text-[#a96d09]";
const PURPLE_BADGE = "bg-[#ece8ff] text-[#6758c7]";
const GREEN_BADGE = "bg-[#dff7ec] text-[#14956c]";
const RED_BADGE = "bg-[#fff1f1] text-[#d95555]";
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
  const d = toKstDisplay(iso);
  return `${d.getUTCMonth() + 1}월 ${d.getUTCDate()}일`;
}

function formatDayLabel(iso: string): string {
  return WEEKDAY_LABELS[toKstDisplay(iso).getUTCDay()];
}

function formatTimeLabel(iso: string): string {
  const d = toKstDisplay(iso);
  const hh = String(d.getUTCHours()).padStart(2, "0");
  const mm = String(d.getUTCMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

// 진행 중인 요청인지
function isActive(request: SentSubstituteRequest): boolean {
  return request.status === "OPEN" || request.status === "ACCEPTED";
}

// 상태별 배지와 설명
function getStatusView(request: SentSubstituteRequest): StatusView {
  if (request.status === "OPEN") {
    return {
      label: "응답 대기",
      description: "대체 근무 요청에 대한 응답을 기다리고 있어요.",
      badgeClassName: YELLOW_BADGE,
    };
  }

  if (request.status === "ACCEPTED") {
    return {
      label: "관리자 승인 대기",
      description: "대체 근무 요청이 수락되었습니다. 관리자 승인을 기다리고 있어요.",
      badgeClassName: PURPLE_BADGE,
    };
  }

  if (request.status === "APPROVED") {
    return {
      label: "승인 완료",
      description: "관리자가 승인해서 근무자가 변경됐어요.",
      badgeClassName: GREEN_BADGE,
    };
  }

  if (request.status === "CLOSED" && request.closeReason === "ALL_CANDIDATES_REJECTED") {
    return {
      label: "후보 전원 거절",
      description: "모든 후보가 요청을 거절했어요.",
      badgeClassName: RED_BADGE,
    };
  }

  if (request.status === "CLOSED" && request.closeReason === "EXPIRED") {
    return {
      label: "기간 만료",
      description: "근무 시작 시각에 만료되었어요.",
      badgeClassName: GRAY_BADGE,
    };
  }

  if (request.status === "CLOSED" && request.closeReason === "MANAGER_CLOSED") {
    return {
      label: "관리자 종료",
      description: "관리자가 요청을 종료했어요.",
      badgeClassName: GRAY_BADGE,
    };
  }

  if (request.status === "CLOSED" && request.closeReason === "SHIFT_CHANGED") {
    return {
      label: "근무 변경",
      description: "근무가 변경되어 요청이 종료되었어요.",
      badgeClassName: GRAY_BADGE,
    };
  }

  if (request.status === "CLOSED" && request.closeReason === "CANDIDATE_INELIGIBLE") {
    return {
      label: "후보 자격 상실",
      description: "진행할 수 있는 후보가 없어 요청이 종료되었어요. 근무는 그대로이니 필요하면 다시 요청해 주세요.",
      badgeClassName: RED_BADGE,
    };
  }

  // 예상 못 한 값이 오면 기본값
  return {
    label: "종료됨",
    description: "종료된 요청이에요.",
    badgeClassName: GRAY_BADGE,
  };
}

// 요청·승인·종료 날짜 한 줄. 만료 시각은 getExpiryLine으로 다른 줄에 보여 줌 (G03-D-19 D19-5)
function getDateLine(request: SentSubstituteRequest): string {
  const parts = [`${formatDateLabel(request.createdAt)} 요청`];

  if (request.status === "APPROVED" && request.approvedAt) {
    parts.push(`${formatDateLabel(request.approvedAt)} 승인`);
  }

  if (
      request.status === "CLOSED" &&
      (request.closeReason === "ALL_CANDIDATES_REJECTED" ||
          request.closeReason === "MANAGER_CLOSED" ||
          request.closeReason === "SHIFT_CHANGED" ||
          request.closeReason === "CANDIDATE_INELIGIBLE") &&
      request.closedAt
  ) {
    parts.push(`${formatDateLabel(request.closedAt)} 종료`);
  }

  return parts.join(" · ");
}

// 만료 요청의 만료 시각 (서버가 closedAt에 근무 시작 시각을 보냄, G03-D-19). 라벨은 "종료일"이 아니라 "만료 시각"
function getExpiryLine(request: SentSubstituteRequest): string | null {
  if (request.status !== "CLOSED" || request.closeReason !== "EXPIRED" || !request.closedAt) {
    return null;
  }

  return `만료 시각 ${formatDateLabel(request.closedAt)} (${formatDayLabel(request.closedAt)}) ${formatTimeLabel(request.closedAt)}`;
}

export default function SentRequestsPage() {
  const params = useParams();
  const workplaceId = Number(params.workplaceId);

  // 보낸 요청 전체, null이면 불러오는 중
  const [requests, setRequests] = useState<SentSubstituteRequest[] | null>(null);
  const [loadError, setLoadError] = useState("");
  const [tab, setTab] = useState<Tab>("ACTIVE");

  useEffect(() => {
    async function loadRequests() {
      try {
        const data = await apiFetch<SentSubstituteRequest[]>(
            "/substitute-requests/sent"
        );
        setRequests(data);
      } catch (fetchError) {
        if (fetchError instanceof ApiError) {
          setLoadError(fetchError.message);
        } else if (
            fetchError instanceof UnexpectedResponseError ||
            fetchError instanceof TypeError
        ) {
          setLoadError("서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        } else {
          console.error(fetchError);
          setLoadError("요청 목록을 불러오는 중 문제가 발생했습니다.");
        }
      }
    }

    loadRequests();
  }, []);

  // 지금 근무지 요청만 남김
  const workplaceRequests = (requests ?? []).filter(
      (request) => request.workplaceId === workplaceId
  );

  // 진행 중은 근무가 가까운 순
  const activeRequests = workplaceRequests
      .filter(isActive)
      .sort(
          (a, b) =>
              parseAsKst(a.startAt).getTime() - parseAsKst(b.startAt).getTime()
      );

  // 지난 요청은 서버 순서(최신 요청 순) 그대로
  const pastRequests = workplaceRequests.filter((request) => !isActive(request));

  const visibleRequests = tab === "ACTIVE" ? activeRequests : pastRequests;

  return (
      <>
        <PageHeader
            title="보낸 요청"
            description="내가 보낸 대체 근무 요청의 진행 상태를 확인하세요."
        />

        {loadError && (
            <p className="text-sm font-semibold text-[#d95555]">
              {loadError}
            </p>
        )}

        {!loadError && requests === null && (
            <p className="text-sm text-[#78847f]">
              요청 목록을 불러오는 중이에요...
            </p>
        )}

        {!loadError && requests !== null && (
            <>
              {/* 탭 */}
              <div className="mb-5 flex flex-wrap gap-2">
                <button
                    type="button"
                    onClick={() => setTab("ACTIVE")}
                    className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                        tab === "ACTIVE"
                            ? "bg-[#005642] text-white"
                            : "text-[#66736d] hover:bg-[#f3fbf7]"
                    }`}
                >
                  진행 중 {activeRequests.length}
                </button>

                <button
                    type="button"
                    onClick={() => setTab("PAST")}
                    className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                        tab === "PAST"
                            ? "bg-[#005642] text-white"
                            : "text-[#66736d] hover:bg-[#f3fbf7]"
                    }`}
                >
                  지난 요청 {pastRequests.length}
                </button>
              </div>

              {/* 요청 목록 */}
              <div className="space-y-4">
                {visibleRequests.map((request) => {
                  const statusView = getStatusView(request);
                  const expiryLine = getExpiryLine(request);

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

                            <p className="mt-1 text-sm text-[#78847f]">
                              {request.workplaceName}
                            </p>
                          </div>

                          <span
                              className={`rounded-full px-3 py-1 text-xs font-bold ${statusView.badgeClassName}`}
                          >
                            {statusView.label}
                          </span>
                        </div>

                        <div className="mt-5 rounded-xl bg-[#f3fbf7] px-4 py-3 text-sm font-bold text-[#66736d]">
                          {statusView.description}
                        </div>

                        {expiryLine && (
                            <p className="mt-3 text-xs font-bold text-[#66736d]">
                              {expiryLine}
                            </p>
                        )}

                        <p className={`${expiryLine ? "mt-1" : "mt-3"} text-xs text-[#9aa5a0]`}>
                          {getDateLine(request)}
                        </p>
                      </div>
                  );
                })}

                {/* 비어있는 탭 */}
                {visibleRequests.length === 0 && (
                    <div className="flex min-h-[240px] flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-[#dce8e2] bg-white text-sm text-[#78847f]">
                      {tab === "ACTIVE" ? "진행 중인 요청이 없어요." : "지난 요청이 없어요."}

                      {tab === "ACTIVE" && (
                          <Link
                              href={`/workplaces/${workplaceId}/substitutes/request`}
                              className="font-bold text-[#005642] hover:underline"
                          >
                            대체 근무 요청하러 가기
                          </Link>
                      )}
                    </div>
                )}
              </div>
            </>
        )}
      </>
  );
}
