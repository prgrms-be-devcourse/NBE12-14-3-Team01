package com.merge.backend.domain.workplace.service

/**
 * Decision Log §4-4의 원본 비교만 수행한다. DB 조회·Lock·권한·진행 중 근무 검사는
 * 이 함수의 책임이 아니며, 호출자는 모든 Lock/Guard 후 새로 읽은 사실을 전달해야 한다.
 * 비교 성공만으로 퇴사 쓰기를 허용해서는 안 된다.
 */
object LeavePreviewComparison {
    fun matches(original: LeavePreviewFacts, current: LeavePreviewFacts): Boolean {
        if (original.member.memberId != current.member.memberId ||
            original.member.role != current.member.role ||
            original.member.joinedAt != current.member.joinedAt
        ) return false
        if (original.publishedShifts.sortedBy { it.shiftId } !=
            current.publishedShifts.sortedBy { it.shiftId }
        ) return false

        // ID뿐 아니라 시각·담당·관계까지 같아야 같은 삭제 효과다.
        if (!original.draftShiftsToDelete.containsAll(current.draftShiftsToDelete)) return false
        if (!original.activeRequestsOnPublishedShifts.containsAll(current.activeRequestsOnPublishedShifts)) {
            return false
        }

        val originalCandidacies = original.candidacies.associateBy { it.candidateId }
        for (candidate in current.candidacies) {
            val previous = originalCandidacies[candidate.candidateId] ?: return false
            // PENDING→ACCEPTED 같은 변경은 종료/이력 효과가 달라지므로 동일 대상이 아니다.
            // 다른 PENDING 수 자체가 아니라 실제 자격 변경·종료 효과를 비교한다.
            if (candidate.copy(
                    otherCurrentPendingCandidates = previous.otherCurrentPendingCandidates,
                    wouldBecomeIneligible = previous.wouldBecomeIneligible,
                    wouldCloseRequest = previous.wouldCloseRequest
                ) != previous
            ) return false
            if (candidate.wouldBecomeIneligible && !previous.wouldBecomeIneligible) return false
            if (candidate.wouldCloseRequest && !previous.wouldCloseRequest) return false
        }

        val originalCandidates = original.candidatesOnPublishedShifts.associateBy { it.candidateId }
        for (candidate in current.candidatesOnPublishedShifts) {
            val previous = originalCandidates[candidate.candidateId] ?: return false
            // 다른 후보의 퇴사는 수신 자격 감소다. 상태·관계 변경이나 자격 복구는 감소가 아니다.
            if (candidate.copy(memberLeftAt = previous.memberLeftAt) != previous) return false
            if (previous.memberLeftAt != null && candidate.memberLeftAt != previous.memberLeftAt) return false
        }
        return true
    }
}
