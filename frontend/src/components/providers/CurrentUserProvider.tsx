"use client";

import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { useRouter } from "next/navigation";
import {
  ApiError,
  apiFetch,
  clearToken,
  getToken,
  refreshAccessToken,
  UnexpectedResponseError,
} from "@/lib/api";

// 로그인한 사람 정보 (GET /auth/me 응답)
export type CurrentUser = {
  id: number;
  email: string;
  name: string;
};

export type CurrentUserStatus = "loading" | "success" | "error";

type CurrentUserContextValue = {
  user: CurrentUser | null;
  status: CurrentUserStatus;
};

const CurrentUserContext = createContext<CurrentUserContextValue | null>(null);

export default function CurrentUserProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [status, setStatus] = useState<CurrentUserStatus>("loading");
  // 연결 오류 화면에서 다시 시도 누르면 올라감
  const [reloadKey, setReloadKey] = useState(0);

  // 로그인한 사람 정보 한 번만 불러옴
  useEffect(() => {
    // 이 Provider가 사라졌으면 늦게 온 응답은 무시
    let ignore = false;

    async function loadCurrentUser() {
      // 로그인 안 했으면 로그인 화면으로
      if (!getToken()) {
        router.replace("/login");
        return;
      }

      try {
        // accessToken만 남고 쿠키는 없어진 경우(브라우저 재시작 등) 로그인처럼 보이다 끊기지 않게 재발급으로 먼저 확인
        await refreshAccessToken();
        const data = await apiFetch<CurrentUser>("/auth/me");
        if (ignore) {
          return;
        }

        setUser(data);
        setStatus("success");
      } catch (error) {
        // 토큰 지우거나 화면 옮기기 전에 먼저 확인
        if (ignore) {
          return;
        }

        // 토큰이 틀렸거나 재발급도 실패하면 토큰 지우고 로그인 화면으로
        if (error instanceof ApiError && error.status === 401) {
          clearToken();
          router.replace("/login");
          return;
        }

        // 서버 꺼짐 등은 만료라고 단정 못 하니까 로그인 화면으로 안 보내고 연결 오류 화면 (토큰 유지)
        if (
            !(error instanceof ApiError) &&
            !(error instanceof UnexpectedResponseError) &&
            !(error instanceof TypeError)
        ) {
          console.error(error);
        }

        setUser(null);
        setStatus("error");
      }
    }

    loadCurrentUser();

    return () => {
      ignore = true;
    };
  }, [router, reloadKey]);

  // 로그인 확인 끝날 때까지 화면 안 그림
  if (status === "loading") {
    return null;
  }

  if (status === "error") {
    return (
        <main className="flex min-h-screen items-center justify-center bg-[#f5faf7] px-4">
          <div className="w-full max-w-md rounded-3xl border border-[#dce8e2] bg-white p-8 text-center shadow-sm">
            <p className="text-sm font-bold text-[#d95555]">
              서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.
            </p>

            <button
                type="button"
                onClick={() => {
                  setStatus("loading");
                  setReloadKey((prev) => prev + 1);
                }}
                className="mt-6 w-full rounded-xl bg-[#005642] px-4 py-3 font-bold text-white transition hover:bg-[#0b6b52]"
            >
              다시 시도
            </button>
          </div>
        </main>
    );
  }

  return (
      <CurrentUserContext.Provider value={{ user, status }}>
        {children}
      </CurrentUserContext.Provider>
  );
}

// 로그인한 사람 정보 꺼내 쓰기
export function useCurrentUser(): CurrentUserContextValue {
  const value = useContext(CurrentUserContext);

  if (!value) {
    throw new Error("useCurrentUser는 CurrentUserProvider 안에서만 쓸 수 있어요.");
  }

  return value;
}
