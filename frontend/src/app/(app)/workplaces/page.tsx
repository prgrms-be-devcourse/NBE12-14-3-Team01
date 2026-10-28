"use client";

import Link from "next/link";
import {useCallback, useEffect, useRef, useState} from "react";
import { apiFetch } from "@/lib/api";
import { logout } from "@/lib/logout";
import {useRouter} from "next/navigation";
import {useCurrentUser} from "@/components/providers/CurrentUserProvider";

type WorkplaceRole = "MANAGER" | "EMPLOYEE";

type MyWorkplaceResponse = {
  workplaceId: number;
  name: string;
  role: WorkplaceRole;
};

type Workplace = {
  id: number;
  name: string;
  role: WorkplaceRole;
  // members: number;
};

export default function WorkplacesPage() {
  const router = useRouter();
  const [workplaces, setWorkplaces] = useState<Workplace[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [workplaceName, setWorkplaceName] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [isCreating, setIsCreating] = useState(false);
  const [isJoining, setIsJoining] = useState(false);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const listRequestIdRef = useRef(0);

  // 로그인한 사람 이름, 동그라미엔 첫 글자
  const {user, status} = useCurrentUser();
  const userName = status === "success" && user ? user.name.trim() : "";
  const userInitial = userName.charAt(0);

  const fetchWorkplaces = useCallback(async () => {
    const requestId = ++listRequestIdRef.current;

    try {
      const data = await apiFetch<MyWorkplaceResponse[]>("/workplaces");

      if (requestId !== listRequestIdRef.current) return;

      setWorkplaces(
          data.map((workplace) => ({
            id: workplace.workplaceId,
            name: workplace.name,
            role: workplace.role,
          }))
      );
    } catch (error) {
      if (requestId !== listRequestIdRef.current) return;

      setLoadError(
          error instanceof Error
              ? error.message
              : "근무지 목록을 불러오지 못했습니다."
      );
    } finally {
      if (requestId === listRequestIdRef.current) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    void fetchWorkplaces();
  }, [fetchWorkplaces]);

  // 서버 로그아웃 후 로그인 화면으로 (실패해도 이동, 안내는 로그인 화면이 보여줌. 뒤로 가기로 못 돌아오게 replace)
  const handleLogout = async () => {
    if (isLoggingOut) {
      return;
    }

    setIsLoggingOut(true);
    await logout();
    router.replace("/login");
  };

  const handleCreateWorkplace = async () => {
    if (isCreating || isLoading || loadError) return;

    const name = workplaceName.trim();

    if (!name) {
      alert("근무지 이름을 입력해주세요.");
      return;
    }

    setIsCreating(true);

    try {
      const created = await apiFetch<MyWorkplaceResponse>("/workplaces", {
        method: "POST",
        body: JSON.stringify({
          name,
        }),
      });

      // 생성 전에 시작된 목록 조회는 더 이상 반영하지 않음
      listRequestIdRef.current += 1;
      setIsLoading(false);
      setLoadError(null);

      setWorkplaces((prev) => {
        if (prev.some((workplace) => workplace.id === created.workplaceId)) {
          return prev;
        }

        return [
          ...prev,
          {
            id: created.workplaceId,
            name: created.name,
            role: created.role,
          },
        ];
      });

      setWorkplaceName("");
    } catch (error) {
      alert(
          error instanceof Error
              ? error.message
              : "근무지 생성에 실패했습니다."
      );
    } finally {
      setIsCreating(false);
    }
  };

  const handleJoinWorkplace = async () => {
    if (isJoining || isLoading || loadError) return;

    const code = inviteCode.trim();

    if (!code) {
      alert("초대 코드를 입력해주세요.");
      return;
    }

    setIsJoining(true);

    try {
      const joined = await apiFetch<MyWorkplaceResponse>("/workplaces/join", {
        method: "POST",
        body: JSON.stringify({
          inviteCode: code,
        }),
      });

      // 참여 전에 시작된 목록 조회는 더 이상 반영하지 않음
      listRequestIdRef.current += 1;
      setIsLoading(false);
      setLoadError(null);

      setWorkplaces((prev) => {
        if (prev.some((workplace) => workplace.id === joined.workplaceId)) {
          return prev;
        }

        return [
          ...prev,
          {
            id: joined.workplaceId,
            name: joined.name,
            role: joined.role,
          },
        ];
      });

      setInviteCode("");
    } catch (error) {
      alert(
          error instanceof Error
              ? error.message
              : "근무지 참여에 실패했습니다."
      );
    } finally {
      setIsJoining(false);
    }
  };

  return (
      <main className="min-h-screen bg-[#f5faf7] px-4 py-8 sm:px-6 sm:py-10">
        <div className="mx-auto max-w-5xl">
          {/* 상단 */}
          <div className="mb-8 flex items-start justify-between gap-4">
            <div>
              <h1 className="text-3xl font-black text-[#005642]">
                SWITCH
              </h1>

              <p className="mt-2 text-[#78847f]">
                참여할 근무지를 선택하거나 새로운 근무지를 시작해보세요.
              </p>
            </div>

            {/* 사용자 */}
            <div className="flex items-center gap-3">
              <div className="hidden min-w-0 text-right sm:block">
                {/* 실패하면 빈칸 (높이는 유지) */}
                <p className="min-h-5 max-w-[160px] truncate text-sm font-black">
                  {userName}
                </p>

                <p className="text-xs text-[#78847f]">
                  로그인 중
                </p>
              </div>

              <div className="grid h-10 w-10 place-items-center rounded-full bg-[#dff7ec] font-black text-[#005642]">
                {userInitial}
              </div>

              <button
                  type="button"
                  onClick={handleLogout}
                  disabled={isLoggingOut}
                  className="rounded-xl border border-[#dce8e2] bg-white px-3 py-2 text-sm font-bold text-[#66736d] transition hover:bg-[#f3fbf7] hover:text-[#005642] disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isLoggingOut ? "로그아웃 중..." : "로그아웃"}
              </button>
            </div>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1.3fr_0.7fr]">
            {/* 내 근무지 */}
            <section>
              <div className="mb-4 flex items-end justify-between">
                <div>
                  <h2 className="text-xl font-black">
                    내 근무지
                  </h2>

                  <p className="mt-1 text-sm text-[#78847f]">
                    현재 참여 중인 근무지입니다.
                  </p>
                </div>

                <span className="text-sm font-bold text-[#005642]">
                  {workplaces.length}개
                </span>
              </div>

              <div className="space-y-3">
                {isLoading && (
                    <div className="rounded-2xl border border-dashed border-[#dce8e2] bg-white p-8 text-center text-sm text-[#78847f]">
                      근무지 목록을 불러오는 중입니다...
                    </div>
                )}

                {!isLoading && loadError && (
                    <div className="rounded-2xl border border-dashed border-[#f1cccc] bg-white p-8 text-center">
                      <p className="text-sm text-[#d95555]">{loadError}</p>

                      <button
                          type="button"
                          onClick={() => {
                            setIsLoading(true);
                            setLoadError(null);
                            void fetchWorkplaces();
                          }}
                          className="mt-4 rounded-xl border border-[#dce8e2] bg-white px-4 py-2 text-sm font-bold text-[#005642] transition hover:bg-[#f3fbf7]"
                      >
                        다시 시도
                      </button>
                    </div>
                )}

                {!isLoading && !loadError && workplaces.map((workplace) => (
                    <Link
                        key={workplace.id}
                        href={`/workplaces/${workplace.id}`}
                        className="block rounded-2xl border border-[#dce8e2] bg-white p-5 transition hover:border-[#14956c] hover:shadow-sm"
                    >
                      <div className="flex items-center justify-between gap-4">
                        <div className="flex min-w-0 items-center gap-4">
                          <div className="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-[#dff7ec] text-xl font-black text-[#005642]">
                            {workplace.name.slice(0, 1)}
                          </div>

                          <div className="min-w-0">
                            <p className="truncate font-black">{workplace.name}</p>
                          </div>
                        </div>

                        <div className="flex shrink-0 items-center gap-3">
                              <span
                                  className={
                                    workplace.role === "MANAGER"
                                        ? "rounded-full bg-[#ece8ff] px-3 py-1 text-xs font-bold text-[#6758c7]"
                                        : "rounded-full bg-[#f3f5f4] px-3 py-1 text-xs font-bold text-[#66736d]"
                                  }
                              >
                                {workplace.role === "MANAGER" ? "관리자" : "직원"}
                              </span>
                          <span className="text-xl text-[#9eaaa5]">›</span>
                        </div>
                      </div>
                    </Link>
                ))}

                {!isLoading && !loadError && workplaces.length === 0 && (
                    <div className="rounded-2xl border border-dashed border-[#dce8e2] bg-white p-8 text-center text-sm text-[#78847f]">
                      참여 중인 근무지가 없습니다.
                    </div>
                )}
              </div>
            </section>

            {/* 근무지 추가 */}
            <section className="space-y-4">
              {/* 새 근무지 */}
              <div className="rounded-2xl border border-[#dce8e2] bg-white p-6">
                <h2 className="text-lg font-black">
                  새 근무지 만들기
                </h2>

                <p className="mt-1 text-sm leading-6 text-[#78847f]">
                  새로운 근무지를 만들면 관리자로 시작합니다.
                </p>

                <div className="mt-5">
                  <label className="mb-2 block text-sm font-bold">
                    근무지 이름
                  </label>

                  <input
                      type="text"
                      value={workplaceName}
                      disabled={isCreating || isLoading || !!loadError}
                      onChange={(e) => setWorkplaceName(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          handleCreateWorkplace();
                        }
                      }}
                      placeholder="예) SWITCH 카페"
                      className="w-full rounded-xl border border-[#dce8e2] px-4 py-3 outline-none transition focus:border-[#14956c]"
                  />
                </div>

                <button
                    type="button"
                    disabled={isCreating || isLoading || !!loadError}
                    onClick={handleCreateWorkplace}
                    className="mt-4 w-full rounded-xl bg-[#005642] px-4 py-3 text-sm font-bold text-white transition hover:bg-[#0b6b52]"
                >
                  근무지 만들기
                </button>
              </div>

              {/* 초대 코드 */}
              <div className="rounded-2xl border border-[#dce8e2] bg-white p-6">
                <h2 className="text-lg font-black">
                  초대 코드로 참여
                </h2>

                <p className="mt-1 text-sm leading-6 text-[#78847f]">
                  관리자에게 받은 코드를 입력해주세요.
                </p>

                <div className="mt-5">
                  <label className="mb-2 block text-sm font-bold">
                    초대 코드
                  </label>

                  <input
                      type="text"
                      value={inviteCode}
                      disabled={isJoining || isLoading || !!loadError}
                      onChange={(e) =>
                          setInviteCode(e.target.value.toUpperCase())
                      }
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          handleJoinWorkplace();
                        }
                      }}
                      placeholder="코드를 입력해주세요"
                      className="w-full rounded-xl border border-[#dce8e2] px-4 py-3 font-bold uppercase tracking-wider outline-none transition focus:border-[#14956c]"
                  />
                </div>

                <button
                    type="button"
                    disabled={isJoining || isLoading || !!loadError}
                    onClick={handleJoinWorkplace}
                    className="mt-4 w-full rounded-xl border border-[#005642] bg-white px-4 py-3 text-sm font-bold text-[#005642] transition hover:bg-[#f3fbf7]"
                >
                  근무지 참여하기
                </button>
              </div>
            </section>
          </div>
        </div>
      </main>
  );
}