"use client";

import { useState } from "react";
import { ApiError, apiFetch } from "@/lib/api";

// 퇴사 확정 (API-LV-02). 미리보기(API-LV-01) 결과를 받아 근무별 선택 → 확정 → 결과까지 처리한다.
// 화면 흐름과 오류 대응은 FRONTEND-CONTRACT §3-1·§3-2

type PreviewShift = {
  shiftId: number;
  startAt: string;
  endAt: string;
};

// 이 화면이 쓰는 미리보기 필드만 받는다 (LeavePreviewResponse의 일부)
export type LeaveConfirmPreview = {
  member: { memberId: number; name: string };
  canLeave: boolean;
  publishedShifts: PreviewShift[];
  previewContext: string;
};

export type LeaveTargetMember = {
  id: number;
  name: string;
};

type Decision = {
  action: "REASSIGN" | "CANCEL" | null;
  targetMemberId: number | null;
};

type UnavailableConflict = {
  shiftId: number;
  shiftStartAt: string;
  shiftEndAt: string;
  targetMemberId: number;
  targetName: string;
  overlaps: { startAt: string; endAt: string }[];
};

type LeaveConfirmResponse = {
  leftAt: string;
  reassignedShifts: PreviewShift[];
  cancelledShifts: PreviewShift[];
  deletedDraftShifts: PreviewShift[];
  closedRequests: { total: number; byReason: Record<string, number> };
  ineligibleCandidacies: number;
  newInviteCode?: string;
};

// LocalDateTime은 서버가 전달한 근무지 시각을 그대로 표시한다 (미리보기 화면과 같은 방식)
function formatTime(value: string) {
  return value.replace("T", " ").slice(0, 16);
}

function formatHourMinute(value: string) {
  return value.replace("T", " ").slice(11, 16);
}

export default function LeaveConfirmPanel({ workplaceId, preview, members, onStale, onDone }: {
  workplaceId: string;
  preview: LeaveConfirmPreview;
  members: LeaveTargetMember[]; // 현재 구성원 (퇴사자 제외는 이 컴포넌트가 함)
  onStale: () => void; // WPL-010: 미리보기를 다시 조회
  onDone: (newInviteCode: string | null) => void; // 결과 확인 후 닫기 (목록에서 빼기, 새 초대 코드 반영)
}) {
  const [decisions, setDecisions] = useState<Record<number, Decision>>(() =>
    Object.fromEntries(preview.publishedShifts.map((shift) => [shift.shiftId, { action: null, targetMemberId: null }]))
  );
  const [regenerateInviteCode, setRegenerateInviteCode] = useState(false);
  const [conflicts, setConflicts] = useState<UnavailableConflict[] | null>(null);
  const [conflictsChecked, setConflictsChecked] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<LeaveConfirmResponse | null>(null);

  const targets = members.filter((member) => member.id !== preview.member.memberId);

  // 모든 근무를 골라야 확정 가능 (인계는 대상까지)
  const allDecided = preview.publishedShifts.every((shift) => {
    const decision = decisions[shift.shiftId];
    return decision?.action === "CANCEL" || (decision?.action === "REASSIGN" && decision.targetMemberId !== null);
  });
  const canSubmit = preview.canLeave && allDecided && !submitting && (conflicts === null || conflictsChecked);

  const updateDecision = (shiftId: number, next: Decision) => {
    setDecisions((prev) => ({ ...prev, [shiftId]: next }));
    // 선택이 바뀌면 이전 충돌 확인은 무효
    setConflicts(null);
    setConflictsChecked(false);
    setError("");
  };

  const handleSubmit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setError("");

    try {
      const data = await apiFetch<LeaveConfirmResponse>(
        `/workplaces/${workplaceId}/members/${preview.member.memberId}/leave`,
        {
          method: "POST",
          body: JSON.stringify({
            previewContext: preview.previewContext,
            decisions: preview.publishedShifts.map((shift) => {
              const decision = decisions[shift.shiftId];
              return {
                shiftId: shift.shiftId,
                shiftStartAt: shift.startAt,
                shiftEndAt: shift.endAt,
                action: decision.action,
                targetMemberId: decision.action === "REASSIGN" ? decision.targetMemberId : null,
              };
            }),
            // 서버가 준 충돌 목록을 확인했으면 그대로 돌려보냄 (§3-2)
            acknowledgedUnavailableConflicts: (conflicts ?? []).map((conflict) => ({
              shiftId: conflict.shiftId,
              targetMemberId: conflict.targetMemberId,
              overlaps: conflict.overlaps,
            })),
            regenerateInviteCode,
          }),
        }
      );
      setResult(data);
    } catch (error) {
      if (!(error instanceof ApiError)) {
        setError("퇴사 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        return;
      }

      if (error.code === "WPL-011") {
        // 불가능 시간 충돌: 목록을 보여 주고 확인받은 뒤 다시 보냄. 또 오면 새 충돌이므로 다시 확인
        const data = error.data as { unavailableConflicts?: UnavailableConflict[] } | null;
        setConflicts(data?.unavailableConflicts ?? []);
        setConflictsChecked(false);
        setError("인계 대상의 근무 불가능 시간과 겹치는 근무가 있습니다. 아래 내용을 확인해 주세요.");
      } else if (error.code === "WPL-010") {
        // 미리보기 뒤 근무·대타·구성원이 바뀜 → 미리보기부터 다시
        alert("퇴사 계획이 변경되었습니다. 미리보기를 다시 조회합니다.");
        onStale();
      } else if (error.code === "SFT-009" || error.code === "SFT-010") {
        // 인계 대상의 다른 공식 근무와 겹침 → 인계 대상을 다시 선택
        setError(`${error.message} 인계 대상을 다시 선택해 주세요. (${error.code})`);
      } else if (error.code === "WPL-009") {
        const data = error.data as { endAt?: string } | null;
        setError(data?.endAt
          ? `진행 중인 근무가 있어 퇴사 처리할 수 없습니다. ${formatTime(data.endAt)} 근무 종료 후 다시 시도해 주세요.`
          : `${error.message} (${error.code})`);
      } else {
        setError(`${error.message} (${error.code})`);
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (result) {
    const shiftChanged = result.closedRequests.byReason.SHIFT_CHANGED ?? 0;
    const candidateIneligible = result.closedRequests.byReason.CANDIDATE_INELIGIBLE ?? 0;

    return (
      <section role="status" className="rounded-xl bg-[#f3fbf7] p-4 text-sm text-[#66736d]">
        <h3 className="font-bold text-[#1f2925]">{preview.member.name} 님의 퇴사 처리를 완료했습니다.</h3>
        <ul className="mt-3 space-y-1">
          <li>인계한 근무 {result.reassignedShifts.length}건 · 취소한 근무 {result.cancelledShifts.length}건 · 삭제한 초안 근무 {result.deletedDraftShifts.length}건</li>
          <li>종료된 대타 요청 {result.closedRequests.total}건 (근무 변경 {shiftChanged}건 · 후보 자격 상실 {candidateIneligible}건)</li>
          <li>자격을 잃은 대타 후보 참여 {result.ineligibleCandidacies}건</li>
          {result.newInviteCode && <li className="font-bold text-[#005642]">새 초대 코드: {result.newInviteCode}</li>}
        </ul>
        <button type="button" onClick={() => onDone(result.newInviteCode ?? null)}
          className="mt-4 rounded-lg bg-[#005642] px-4 py-2 font-bold text-white hover:bg-[#0b6b52]">
          확인
        </button>
      </section>
    );
  }

  return (
    <section className="space-y-4 text-sm text-[#66736d]">
      <div>
        <h3 className="font-bold text-[#1f2925]">근무별 처리 선택</h3>
        <p className="mt-1 text-xs">공식 근무마다 인계(다른 직원) 또는 취소를 골라야 퇴사를 확정할 수 있습니다.</p>
      </div>

      {preview.publishedShifts.length === 0 && <p>처리할 공식 근무가 없습니다.</p>}

      <ul className="space-y-2">
        {preview.publishedShifts.map((shift) => {
          const decision = decisions[shift.shiftId];

          return (
            <li key={shift.shiftId} className="rounded-xl border border-[#dce8e2] p-4">
              <p className="font-bold text-[#1f2925]">{formatTime(shift.startAt)} ~ {formatTime(shift.endAt)}</p>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <label className="flex items-center gap-1">
                  <input type="radio" name={`leave-decision-${shift.shiftId}`} disabled={submitting}
                    checked={decision.action === "REASSIGN"}
                    onChange={() => updateDecision(shift.shiftId, { action: "REASSIGN", targetMemberId: decision.targetMemberId })} />
                  인계
                </label>
                <select aria-label="인계 대상" disabled={submitting || decision.action !== "REASSIGN"}
                  value={decision.targetMemberId ?? ""}
                  onChange={(event) => updateDecision(shift.shiftId, {
                    action: "REASSIGN",
                    targetMemberId: event.target.value ? Number(event.target.value) : null,
                  })}
                  className="rounded-lg border border-[#dce8e2] px-2 py-1 disabled:opacity-50">
                  <option value="">직원 선택</option>
                  {targets.map((member) => <option key={member.id} value={member.id}>{member.name}</option>)}
                </select>
                <label className="ml-2 flex items-center gap-1">
                  <input type="radio" name={`leave-decision-${shift.shiftId}`} disabled={submitting}
                    checked={decision.action === "CANCEL"}
                    onChange={() => updateDecision(shift.shiftId, { action: "CANCEL", targetMemberId: null })} />
                  취소
                </label>
              </div>
            </li>
          );
        })}
      </ul>

      {conflicts && (
        <div role="alert" className="rounded-xl bg-[#fff8e8] p-4">
          <p className="font-bold text-[#a96d09]">근무 불가능 시간 충돌</p>
          <ul className="mt-2 space-y-1">
            {conflicts.map((conflict) => (
              <li key={`${conflict.shiftId}-${conflict.targetMemberId}`}>
                {formatTime(conflict.shiftStartAt)} ~ {formatHourMinute(conflict.shiftEndAt)} · {conflict.targetName} ·{" "}
                {conflict.overlaps.map((overlap) => `${formatHourMinute(overlap.startAt)}~${formatHourMinute(overlap.endAt)}`).join(", ")} 겹침
              </li>
            ))}
          </ul>
          <label className="mt-3 flex items-center gap-2 font-bold">
            <input type="checkbox" checked={conflictsChecked} disabled={submitting}
              onChange={(event) => setConflictsChecked(event.target.checked)} />
            위 충돌을 확인했습니다
          </label>
        </div>
      )}

      <label className="flex items-center gap-2">
        <input type="checkbox" checked={regenerateInviteCode} disabled={submitting}
          onChange={(event) => setRegenerateInviteCode(event.target.checked)} />
        초대 코드 재발급
      </label>

      {error && <p role="alert" className="rounded-xl bg-[#fff7f7] p-3 text-[#d95555]">{error}</p>}

      <button type="button" disabled={!canSubmit} onClick={handleSubmit}
        className="w-full rounded-xl bg-[#d95555] px-4 py-3 font-bold text-white transition hover:bg-[#c94545] disabled:cursor-not-allowed disabled:opacity-50">
        {submitting ? "퇴사 처리 중..." : "퇴사 확정"}
      </button>
    </section>
  );
}
