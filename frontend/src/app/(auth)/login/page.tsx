"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, useSyncExternalStore } from "react";
import {
  ApiError,
  apiFetch,
  setToken,
  UnexpectedResponseError,
} from "@/lib/api";
import {
  clearPendingLogout,
  getPendingLogout,
  retryLogout,
  subscribePendingLogout,
} from "@/lib/logout";

// 로그인 성공 시 서버가 data에 담아주는 값
type LoginResponse = {
  user: {
    id: number;
    email: string;
    name: string;
  };
  accessToken: string;
};

// 회원가입하고 넘어왔을 때만 안내 보여줌
function SignupSuccessNotice() {
  const searchParams = useSearchParams();

  if (searchParams.get("signup") !== "success") {
    return null;
  }

  return (
      <div className="mt-6 rounded-xl bg-[#dff7ec] px-4 py-3 text-sm font-bold text-[#14956c]">
        회원가입이 완료됐어요. 로그인해주세요.
      </div>
  );
}

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isRetryingLogout, setIsRetryingLogout] = useState(false);

  // 서버 로그아웃이 확인 안 됐으면 안내와 다시 시도를 보여줌 (서버 렌더링 땐 sessionStorage가 없어서 null)
  const pendingLogout = useSyncExternalStore(
      subscribePendingLogout,
      getPendingLogout,
      () => null
  );

  const handleRetryLogout = async () => {
    setIsRetryingLogout(true);
    await retryLogout();
    setIsRetryingLogout(false);
  };

  const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setErrorMessage("");
    setIsLoading(true);

    try {
      const data = await apiFetch<LoginResponse>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });

      setToken(data.accessToken);
      clearPendingLogout();
      router.push("/workplaces");
    } catch (error) {
      if (error instanceof ApiError) {
        // 서버가 준 실패 메시지를 그대로 보여줌
        setErrorMessage(error.message);
      } else if (
          error instanceof UnexpectedResponseError ||
          error instanceof TypeError
      ) {
        // 서버 다운, 네트워크 끊김, 서버가 이상한 응답을 준 경우
        setErrorMessage("서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
      } else {
        // 그 외 예상 못한 문제는 원인 확인용으로 콘솔에 남김
        console.error(error);
        setErrorMessage("로그인 중 문제가 발생했습니다. 다시 시도해주세요.");
      }
    } finally {
      // 성공이든 실패든 끝나면 버튼 다시 풀어줌
      setIsLoading(false);
    }
  };

  return (
      <main className="flex min-h-screen items-center justify-center bg-[#f5faf7] px-4">
        <div className="w-full max-w-md rounded-3xl border border-[#dce8e2] bg-white p-8 shadow-sm">
          <div className="text-center">
            <Image
                src="/switch-logo.png"
                alt="SWITCH"
                width={220}
                height={165}
                priority
                className="mx-auto h-auto w-[220px]"
            />
          </div>

          <div className="mt-8">
            <h2 className="text-xl font-black">
              로그인
            </h2>

            <p className="mt-1 text-sm text-[#78847f]">
              계정 정보를 입력해주세요.
            </p>
          </div>

          {pendingLogout && (
              <div className="mt-6 rounded-xl bg-[#fff5f5] px-4 py-3 text-sm font-bold text-[#d95555]">
                <p>
                  {pendingLogout === "forbidden"
                      ? "보안 확인에 실패했습니다. 관리자에게 문의해 주세요."
                      : "서버 로그아웃을 확인하지 못했습니다. 다시 시도해 주세요."}
                </p>

                <button
                    type="button"
                    onClick={handleRetryLogout}
                    disabled={isRetryingLogout}
                    className="mt-3 rounded-xl border border-[#d95555] bg-white px-3 py-2 text-sm font-bold text-[#d95555] transition hover:bg-[#fff5f5] disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isRetryingLogout ? "다시 시도 중..." : "다시 시도"}
                </button>
              </div>
          )}

          {/* 에러가 있으면 가입 안내는 숨김 */}
          {!errorMessage && (
              <Suspense fallback={null}>
                <SignupSuccessNotice />
              </Suspense>
          )}

          <form
              onSubmit={handleLogin}
              className="mt-6 space-y-5"
          >
            <div>
              <label className="mb-2 block text-sm font-bold">
                이메일
              </label>

              <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoComplete="email"
                  placeholder="example@switch.com"
                  className="w-full rounded-xl border border-[#dce8e2] px-4 py-3 outline-none transition focus:border-[#14956c]"
              />
            </div>

            <div>
              <label className="mb-2 block text-sm font-bold">
                비밀번호
              </label>

              <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                  placeholder="비밀번호를 입력해주세요."
                  className="w-full rounded-xl border border-[#dce8e2] px-4 py-3 outline-none transition focus:border-[#14956c]"
              />
            </div>

            {errorMessage && (
                <p className="text-sm font-bold text-[#d95555]">
                  {errorMessage}
                </p>
            )}

            <button
                type="submit"
                disabled={isLoading}
                className="w-full rounded-xl bg-[#005642] px-4 py-3 font-bold text-white transition hover:bg-[#0b6b52] disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isLoading ? "로그인 중..." : "로그인"}
            </button>
          </form>

          <div className="mt-6 text-center text-sm text-[#78847f]">
            아직 계정이 없으신가요?

            <Link
                href="/signup"
                className="ml-2 font-bold text-[#005642] hover:underline"
            >
              회원가입
            </Link>
          </div>
        </div>
      </main>
  );
}