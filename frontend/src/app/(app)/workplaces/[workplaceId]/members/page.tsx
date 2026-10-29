"use client";

import { useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import Card from "@/components/ui/Card";
import PageHeader from "@/components/ui/PageHeader";
import { ApiError, apiFetch } from "@/lib/api";
import LeaveConfirmPanel from "./LeaveConfirmPanel";

type MemberRole = "MANAGER" | "EMPLOYEE";

type MyWorkplaceResponse = {
  workplaceId: number;
  name: string;
  role: MemberRole;
};

type AccessStatus =
    "loading"
    | "manager"
    | "employee"
    | "not-found"
    | "error";

type MemberResponse = {
  memberId: number;
  name: string;
  email: string;
  role: MemberRole;
};

type Member = {
  id: number;
  name: string;
  email: string;
  role: MemberRole;
  leftAt: string | null;
};

type InviteCodeResponse = {
  inviteCode: string;
};

type PreviewShift = {
  shiftId: number;
  memberId: number;
  scheduleId: number;
  scheduleStatus: "DRAFT" | "PUBLISHED";
  shiftStatus: "SCHEDULED" | "CANCELLED";
  startAt: string;
  endAt: string;
};

type WorkShift = PreviewShift & { weekStartDate: string };
type RequestStatus = "OPEN" | "ACCEPTED" | "APPROVED" | "CLOSED";
type CandidateStatus = "PENDING" | "ACCEPTED" | "REJECTED" | "INELIGIBLE";

// API-LV-01 / LeavePreviewResponse. apiFetch가 응답 envelope의 data를 반환한다.
type LeavePreviewResponse = {
  member: { memberId: number; name: string; role: MemberRole; joinedAt: string };
  canLeave: boolean;
  blockingReason: string | null;
  ongoingShift: WorkShift | null;
  publishedShifts: (PreviewShift & {
    activeRequests: {
      requestId: number;
      requesterMemberId: number;
      status: RequestStatus;
      wouldCloseRequest: boolean;
    }[];
  })[];
  draftShiftsToDelete: WorkShift[];
  candidacies: {
    candidateId: number;
    requestId: number;
    requesterMemberId: number;
    shift: WorkShift;
    requestStatus: RequestStatus;
    candidateStatus: CandidateStatus;
    otherCurrentPendingCandidates: number;
    wouldBecomeIneligible: boolean;
    wouldCloseRequest: boolean;
  }[];
  previewContext: string;
};

const requestStatusLabels: Record<RequestStatus, string> = {
  OPEN: "진행 중", ACCEPTED: "수락 · 승인 대기", APPROVED: "승인됨", CLOSED: "종료됨",
};
const candidateStatusLabels: Record<CandidateStatus, string> = {
  PENDING: "응답 대기", ACCEPTED: "수락", REJECTED: "거절", INELIGIBLE: "후보 자격 상실",
};

// LocalDateTime은 서버가 전달한 근무지 시각을 그대로 표시한다.
function formatPreviewTime(value: string) {
  return value.replace("T", " ").slice(0, 16);
}

function ShiftSummary({ shift }: { shift: PreviewShift }) {
  return (
    <>
      <p className="font-bold">{formatPreviewTime(shift.startAt)} ~ {formatPreviewTime(shift.endAt)}</p>
      <p className="mt-1 text-xs text-[#78847f]">
        근무 #{shift.shiftId} · 근무표 #{shift.scheduleId} · {shift.scheduleStatus === "PUBLISHED" ? "공개" : "초안"}
        {" · "}{shift.shiftStatus === "SCHEDULED" ? "예정" : "취소"}
      </p>
    </>
  );
}

function LeavePreviewModal({ workplaceId, member, members, onClose, onLeft }: {
  workplaceId: string;
  member: Member;
  members: Member[];
  onClose: () => void;
  onLeft: (newInviteCode: string | null) => void;
}) {
  const [preview, setPreview] = useState<LeavePreviewResponse | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previousFocus = document.activeElement;
    closeButtonRef.current?.focus({ preventScroll: true });
    return () => {
      if (previousFocus instanceof HTMLElement) previousFocus.focus();
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    const loadPreview = async () => {
      try {
        const data = await apiFetch<LeavePreviewResponse>(
          `/workplaces/${workplaceId}/members/${member.id}/leave-preview`,
          { method: "GET", signal: controller.signal, cache: "no-store" }
        );
        if (cancelled) return;
        if (data.member.memberId !== member.id || data.member.role !== "EMPLOYEE") {
          setError("퇴사 대상 정보가 일치하지 않습니다. 다시 조회해 주세요.");
          return;
        }
        setPreview(data);
      } catch (error) {
        if (cancelled) return;
        setError(error instanceof ApiError
          ? `${error.message} (${error.code})`
          : "퇴사 미리보기를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void loadPreview();
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [workplaceId, member.id, attempt]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 px-4"
      onKeyDown={(event) => {
        if (event.key === "Escape") onClose();
        if (event.key === "Tab") {
          const buttons = event.currentTarget.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
          const first = buttons[0];
          const last = buttons[buttons.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
          }
        }
      }}>
      <div role="dialog" aria-modal="true" aria-labelledby="leave-preview-title" aria-describedby="leave-preview-notice"
        className="max-h-[90dvh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
        <h2 id="leave-preview-title" className="text-xl font-black">퇴사 미리보기</h2>
        <p className="mt-3 text-sm text-[#66736d]">
          <strong className="text-[#1f2925]">{preview?.member.name ?? member.name}</strong> · {member.email}
        </p>
        <p id="leave-preview-notice" className="mt-4 rounded-xl bg-[#fff7f7] p-4 text-sm font-bold leading-6 text-[#d95555]">
          아래에서 근무별 처리를 고른 뒤 퇴사를 확정합니다. 확정하기 전까지 소속·근무·대타 정보는 바뀌지 않습니다.
        </p>

        <div aria-live="polite" aria-busy={loading} className="mt-5 space-y-5 text-sm text-[#66736d]">
          {loading && <p role="status">퇴사 영향을 조회하는 중입니다...</p>}
          {error && <div role="alert" className="rounded-xl bg-[#fff7f7] p-4">
            <p className="text-[#d95555]">{error}</p>
            <button type="button" disabled={loading} onClick={() => {
              if (loading) return;
              setLoading(true);
              setError("");
              setPreview(null);
              setAttempt((value) => value + 1);
            }} className="mt-3 rounded-lg border border-[#dce8e2] px-3 py-2 font-bold hover:bg-[#f3fbf7] disabled:opacity-50">
              다시 시도
            </button>
          </div>}
          {preview && <>
            <p>소속 #{preview.member.memberId} · {preview.member.role === "EMPLOYEE" ? "직원" : "관리자"} · 가입 {formatPreviewTime(preview.member.joinedAt)}</p>
            <section className={`rounded-xl p-4 ${preview.canLeave ? "bg-[#f3fbf7]" : "bg-[#fff7f7]"}`}>
              <h3 className="font-bold text-[#1f2925]">{preview.canLeave ? "조회 시점에 진행 중인 근무가 없습니다." : "현재 퇴사할 수 없습니다."}</h3>
              {!preview.canLeave && <p className="mt-2 text-[#d95555]">
                {preview.blockingReason === "ONGOING_SHIFT"
                  ? "진행 중인 공식 근무가 있습니다. 근무 종료 후 다시 조회해 주세요."
                  : `퇴사 불가 사유: ${preview.blockingReason ?? "서버에서 퇴사 불가로 안내했습니다."}`}
              </p>}
              {!preview.canLeave && preview.ongoingShift && <div className="mt-3"><ShiftSummary shift={preview.ongoingShift} /></div>}
            </section>
            <p className="text-xs">아래는 조회 시점의 예상 영향입니다. 실제 처리 결과가 아니며, 근무·대타 상태에 따라 달라질 수 있습니다.</p>
            <section>
              <h3 className="font-bold text-[#1f2925]">공식 근무 ({preview.publishedShifts.length}건)</h3>
              <p className="mt-1 text-xs">아래 근무별 처리 선택에서 근무마다 인계 또는 취소를 골라야 합니다.</p>
              {preview.publishedShifts.length === 0 && <p className="mt-2">대상 공식 근무가 없습니다.</p>}
              <ul className="mt-2 space-y-2">{preview.publishedShifts.map((shift) => <li key={shift.shiftId} className="rounded-xl bg-[#f3fbf7] p-4">
                <ShiftSummary shift={shift} />
                {shift.activeRequests.length === 0 && <p className="mt-2 text-xs">영향받는 대타 요청 없음</p>}
                <ul className="mt-2 space-y-1">{shift.activeRequests.map((request) => <li key={request.requestId}>
                  대타 요청 #{request.requestId} · {requestStatusLabels[request.status]} · {request.wouldCloseRequest ? "종료 예정" : "종료 영향 없음"}
                </li>)}</ul>
              </li>)}</ul>
            </section>
            <section>
              <h3 className="font-bold text-[#1f2925]">삭제 예정 DRAFT 근무 ({preview.draftShiftsToDelete.length}건)</h3>
              {preview.draftShiftsToDelete.length === 0 && <p className="mt-2">삭제 예정인 초안 근무가 없습니다.</p>}
              <ul className="mt-2 space-y-2">{preview.draftShiftsToDelete.map((shift) => <li key={shift.shiftId} className="rounded-xl bg-[#f3fbf7] p-4">
                <ShiftSummary shift={shift} />
              </li>)}</ul>
            </section>
            <section>
              <h3 className="font-bold text-[#1f2925]">대타 후보 영향 ({preview.candidacies.length}건)</h3>
              {preview.candidacies.length === 0 && <p className="mt-2">영향받는 대타 후보 참여가 없습니다.</p>}
              <ul className="mt-2 space-y-2">{preview.candidacies.map((candidate) => <li key={candidate.candidateId} className="rounded-xl bg-[#f3fbf7] p-4">
                <p className="mb-2 font-bold">요청 #{candidate.requestId} · 후보 #{candidate.candidateId}</p>
                <ShiftSummary shift={candidate.shift} />
                <p className="mt-2">요청: {requestStatusLabels[candidate.requestStatus]} · 후보: {candidateStatusLabels[candidate.candidateStatus]}</p>
                <p className="mt-1">다른 현재 대기 후보 {candidate.otherCurrentPendingCandidates}명</p>
                <p className="mt-1">{candidate.wouldBecomeIneligible ? "후보 자격 상실 예정" : "후보 상태 이력 유지"} · {candidate.wouldCloseRequest ? "요청 종료 예정" : "요청 종료 영향 없음"}</p>
              </li>)}</ul>
            </section>
            {preview.canLeave && <LeaveConfirmPanel
              workplaceId={workplaceId}
              preview={preview}
              members={members.filter((target) => target.leftAt === null).map((target) => ({ id: target.id, name: target.name }))}
              onStale={() => {
                setLoading(true);
                setError("");
                setPreview(null);
                setAttempt((value) => value + 1);
              }}
              onDone={onLeft}
            />}
          </>}
        </div>
        <div className="mt-6 flex gap-3">
          <button ref={closeButtonRef} type="button" onClick={onClose} className="flex-1 rounded-xl border border-[#dce8e2] px-4 py-3 text-sm font-bold text-[#66736d] hover:bg-[#f3fbf7]">닫기</button>
        </div>
      </div>
    </div>
  );
}

export default function MembersPage() {
  const params = useParams();
  const workplaceId = params.workplaceId as string;

  const [members, setMembers] = useState<Member[]>([]);
  const [inviteCode, setInviteCode] = useState("");

  const [accessStatus, setAccessStatus] =
      useState<AccessStatus>("loading");
  const [loadedWorkplaceId, setLoadedWorkplaceId] =
      useState<string | null>(null);

  const currentAccessStatus =
      loadedWorkplaceId === workplaceId
          ? accessStatus
          : "loading";

  const [filter, setFilter] = useState<
      "ALL" | "MANAGER" | "EMPLOYEE"
  >("ALL");

  const [showInviteModal, setShowInviteModal] = useState(false);
  const [showCopyToast, setShowCopyToast] = useState(false);
  const [openMenuId, setOpenMenuId] = useState<number | null>(null);
  const [memberToEnd, setMemberToEnd] = useState<{ workplaceId: string; member: Member } | null>(null);

  useEffect(() => {
    let cancelled = false;

    const fetchMembersData = async () => {
      try {
        // 먼저 현재 근무지와 역할 확인
        const workplaces =
            await apiFetch<MyWorkplaceResponse[]>("/workplaces");

        if (cancelled) return;

        setMemberToEnd(null);

        const currentWorkplace = workplaces.find(
            (workplace) =>
                workplace.workplaceId === Number(workplaceId)
        );

        if (!currentWorkplace) {
          setMembers([]);
          setInviteCode("");
          setAccessStatus("not-found");
          setLoadedWorkplaceId(workplaceId);
          return;
        }

        if (currentWorkplace.role !== "MANAGER") {
          setMembers([]);
          setInviteCode("");
          setAccessStatus("employee");
          setLoadedWorkplaceId(workplaceId);
          return;
        }

        // 관리자 확인 후에만 관리자 전용 API 호출
        const [memberData, inviteData] = await Promise.all([
          apiFetch<MemberResponse[]>(
              `/workplaces/${workplaceId}/members`
          ),
          apiFetch<InviteCodeResponse>(
              `/workplaces/${workplaceId}/invite-code`
          ),
        ]);

        if (cancelled) return;

        setMembers(
            memberData.map((member) => ({
              id: member.memberId,
              name: member.name,
              email: member.email,
              role: member.role,
              leftAt: null,
            }))
        );

        setInviteCode(inviteData.inviteCode);
        setAccessStatus("manager");
        setLoadedWorkplaceId(workplaceId);
      } catch (error) {
        if (cancelled) return;

        console.error(error);
        setMembers([]);
        setInviteCode("");
        setAccessStatus("error");
        setLoadedWorkplaceId(workplaceId);
      }
    };

    void fetchMembersData();

    return () => {
      cancelled = true;
    };
  }, [workplaceId]);

  const filteredMembers = members.filter((member) => {
    if (filter === "ALL") {
      return true;
    }

    return member.role === filter;
  });

  const managerCount = members.filter(
      (member) => member.role === "MANAGER"
  ).length;

  const employeeCount = members.filter(
      (member) => member.role === "EMPLOYEE"
  ).length;

  const handleCopyInviteCode = async () => {
    if (!inviteCode) return;

    await navigator.clipboard.writeText(inviteCode);

    setShowCopyToast(true);

    setTimeout(() => {
      setShowCopyToast(false);
    }, 2000);
  };

  if (currentAccessStatus === "loading") {
    return (
        <Card>
          <p className="text-sm text-[#78847f]">
            권한을 확인하는 중입니다...
          </p>
        </Card>
    );
  }

  if (currentAccessStatus === "error") {
    return (
        <Card>
          <p className="text-sm text-[#d95555]">
            근무지 정보를 불러오지 못했습니다.
          </p>
        </Card>
    );
  }

  if (currentAccessStatus === "not-found") {
    return (
        <Card>
          <p className="text-sm text-[#78847f]">
            현재 참여 중인 근무지가 아닙니다.
          </p>
        </Card>
    );
  }

  if (currentAccessStatus === "employee") {
    return (
        <Card>
          <p className="text-sm text-[#78847f]">
            직원 목록은 관리자만 확인할 수 있습니다.
          </p>
        </Card>
    );
  }

  return (
      <>
        <PageHeader
            title="직원 목록"
            description="근무지 구성원과 소속 상태를 확인하세요."
        >
          <button
              type="button"
              onClick={() => setShowInviteModal(true)}
              className="rounded-xl bg-[#005642] px-4 py-2.5 text-sm font-bold text-white transition hover:bg-[#0b6b52]"
          >
            + 직원 초대
          </button>
        </PageHeader>

        <Card>
          {/* 필터 */}
          <div className="flex gap-2">
            <button
                type="button"
                onClick={() => setFilter("ALL")}
                className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                    filter === "ALL"
                        ? "bg-[#005642] text-white"
                        : "bg-[#f3fbf7] text-[#66736d] hover:bg-[#dff7ec]"
                }`}
            >
              전체 {members.length}
            </button>

            <button
                type="button"
                onClick={() => setFilter("MANAGER")}
                className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                    filter === "MANAGER"
                        ? "bg-[#005642] text-white"
                        : "bg-[#f3fbf7] text-[#66736d] hover:bg-[#dff7ec]"
                }`}
            >
              관리자 {managerCount}
            </button>

            <button
                type="button"
                onClick={() => setFilter("EMPLOYEE")}
                className={`rounded-full px-4 py-2 text-sm font-bold transition ${
                    filter === "EMPLOYEE"
                        ? "bg-[#005642] text-white"
                        : "bg-[#f3fbf7] text-[#66736d] hover:bg-[#dff7ec]"
                }`}
            >
              직원 {employeeCount}
            </button>
          </div>

          {/* 직원 목록 */}
          <div className="overflow-x-auto md:overflow-visible">
            <table className="w-full min-w-[760px] border-collapse">
              <thead>
              <tr className="border-b border-[#dce8e2] text-left text-sm text-[#78847f]">
                <th className="px-3 py-3">이름</th>
                <th className="px-3 py-3">이메일</th>
                <th className="px-3 py-3">역할</th>
                <th className="px-3 py-3">상태</th>
                <th className="px-3 py-3 text-right">관리</th>
              </tr>
              </thead>

              <tbody>
              {filteredMembers.map((member) => {
                const isActive = member.leftAt === null;

                return (
                    <tr
                        key={member.id}
                        className={`border-b border-[#edf2ef] last:border-none ${
                            !isActive ? "bg-[#fafbfa]" : ""
                        }`}
                    >
                      {/* 이름 */}
                      <td className="px-3 py-4">
                        <div className="flex items-center gap-3">
                          <div
                              className={`grid h-10 w-10 place-items-center rounded-full font-bold ${
                                  isActive
                                      ? "bg-[#dff7ec] text-[#005642]"
                                      : "bg-[#f0f2f1] text-[#9aa5a0]"
                              }`}
                          >
                            {member.name.slice(0, 1)}
                          </div>

                          <span
                              className={`font-bold ${
                                  !isActive ? "text-[#9aa5a0]" : ""
                              }`}
                          >
                          {member.name}
                        </span>
                        </div>
                      </td>

                      {/* 이메일 */}
                      <td
                          className={`px-3 py-4 text-sm ${
                              isActive
                                  ? "text-[#66736d]"
                                  : "text-[#a6afab]"
                          }`}
                      >
                        {member.email}
                      </td>

                      {/* 역할 */}
                      <td className="px-3 py-4">
                      <span
                          className={
                            member.role === "MANAGER"
                                ? "rounded-full bg-[#ece8ff] px-3 py-1 text-xs font-bold text-[#6758c7]"
                                : "rounded-full bg-[#f3f5f4] px-3 py-1 text-xs font-bold text-[#66736d]"
                          }
                      >
                        {member.role === "MANAGER"
                            ? "관리자"
                            : "직원"}
                      </span>
                      </td>

                      {/* 상태 */}
                      <td className="px-3 py-4">
                        {isActive ? (
                            <span className="rounded-full bg-[#dff7ec] px-3 py-1 text-xs font-bold text-[#14956c]">
                          활성
                        </span>
                        ) : (
                            <span className="rounded-full bg-[#f1f3f2] px-3 py-1 text-xs font-bold text-[#78847f]">
                          소속 종료
                        </span>
                        )}
                      </td>

                      {/* 관리 */}
                      <td className="px-3 py-4">
                        <div className="relative flex justify-end">
                          {member.role === "EMPLOYEE" && isActive ? (
                              <>
                                <button
                                    type="button"
                                    onClick={() =>
                                        setOpenMenuId(
                                            openMenuId === member.id
                                                ? null
                                                : member.id
                                        )
                                    }
                                    className="rounded-lg px-3 py-2 text-xl leading-none text-[#78847f] transition hover:bg-[#f3fbf7] hover:text-[#005642]"
                                    aria-label={`${member.name} 관리 메뉴`}
                                >
                                  ⋮
                                </button>

                                {openMenuId === member.id && (
                                    <div className="absolute -left-24 top-10 z-30 w-36 overflow-hidden rounded-xl border border-[#dce8e2] bg-white shadow-lg">
                                      <button
                                          type="button"
                                          onClick={() => {
                                            if (currentAccessStatus !== "manager" || memberToEnd) return;
                                            setMemberToEnd({ workplaceId, member });
                                            setOpenMenuId(null);
                                          }}
                                          title="퇴사 영향을 확인한 뒤 근무별 처리를 골라 퇴사를 확정합니다."
                                          className="w-full px-4 py-3 text-left text-sm font-bold text-[#d95555] hover:bg-[#fff7f7]"
                                      >
                                        소속 종료
                                      </button>
                                    </div>
                                )}
                              </>
                          ) : (
                              <span className="px-3 text-sm text-[#b0b8b4]">
                            -
                          </span>
                          )}
                        </div>
                      </td>
                    </tr>
                );
              })}
              </tbody>
            </table>
          </div>
        </Card>

        {/* 근무지·대상이 바뀌면 기존 모달과 요청을 폐기한다. */}
        {memberToEnd?.workplaceId === workplaceId && (
          <LeavePreviewModal
            key={`${workplaceId}:${memberToEnd.member.id}`}
            workplaceId={workplaceId}
            member={memberToEnd.member}
            members={members}
            onClose={() => setMemberToEnd(null)}
            onLeft={(newInviteCode) => {
              const leftMemberId = memberToEnd.member.id;
              setMembers((prev) => prev.filter((target) => target.id !== leftMemberId));
              if (newInviteCode) setInviteCode(newInviteCode);
              setMemberToEnd(null);
            }}
          />
        )}
        {/* 직원 초대 모달 */}
        {showInviteModal && (
            <div className="fixed inset-0 z-40 flex items-center justify-center bg-black/30 px-4">
              <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl">
                <div className="flex items-start justify-between">
                  <div>
                    <h2 className="text-xl font-black">
                      직원 초대
                    </h2>

                    <p className="mt-1 text-sm text-[#78847f]">
                      아래 초대 코드를 직원에게 공유해주세요.
                    </p>
                  </div>

                  <button
                      type="button"
                      onClick={() => setShowInviteModal(false)}
                      className="text-xl text-[#78847f] hover:text-black"
                  >
                    ×
                  </button>
                </div>

                <div className="mt-6 rounded-xl bg-[#f3fbf7] p-5 text-center">
                  <p className="text-sm text-[#78847f]">
                    초대 코드
                  </p>

                  <p className="mt-2 text-2xl font-black tracking-widest text-[#005642]">
                    {inviteCode}
                  </p>
                </div>

                <p className="mt-4 text-sm leading-6 text-[#78847f]">
                  직원은 로그인 후 근무지 참여 화면에서 이 코드를
                  입력하면 근무지에 참여할 수 있어요.
                </p>

                <div className="mt-6 flex gap-3">
                  <button
                      type="button"
                      onClick={() => setShowInviteModal(false)}
                      className="flex-1 rounded-xl border border-[#dce8e2] px-4 py-3 text-sm font-bold text-[#66736d] hover:bg-[#f3fbf7]"
                  >
                    닫기
                  </button>

                  <button
                      type="button"
                      onClick={handleCopyInviteCode}
                      disabled={!inviteCode}
                      className="flex-1 rounded-xl bg-[#005642] px-4 py-3 text-sm font-bold text-white hover:bg-[#0b6b52] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    초대 코드 복사
                  </button>
                </div>
              </div>
            </div>
        )}

        {showCopyToast && (
            <div className="fixed bottom-6 right-6 z-50 rounded-xl bg-[#005642] px-4 py-3 text-sm font-bold text-white shadow-lg">
              초대 코드를 복사했어요.
            </div>
        )}
      </>
  );
}
