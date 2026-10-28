import {
  ApiError,
  apiFetch,
  clearToken,
  getToken,
  UnexpectedResponseError,
} from "@/lib/api";

// 서버 로그아웃이 확인 안 됐을 때 남기는 표시 (인증 정보 아님, 이 탭에서만 남음)
const PENDING_LOGOUT_KEY = "pendingLogout";

// forbidden: 출처 확인 실패(403), 다시 해도 같을 수 있음 / unconfirmed: 네트워크·서버 문제
export type PendingLogout = "forbidden" | "unconfirmed";

// 표시가 바뀌면 로그인 화면이 다시 그리게 알려줌
const listeners = new Set<() => void>();

export function subscribePendingLogout(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getPendingLogout(): PendingLogout | null {
  const value = sessionStorage.getItem(PENDING_LOGOUT_KEY);
  return value === "forbidden" || value === "unconfirmed" ? value : null;
}

function setPendingLogout(value: PendingLogout | null) {
  if (value) {
    sessionStorage.setItem(PENDING_LOGOUT_KEY, value);
  } else {
    sessionStorage.removeItem(PENDING_LOGOUT_KEY);
  }
  listeners.forEach((listener) => listener());
}

// 로그인 성공하면 씀, 남은 다시 시도가 새 로그인 쿠키를 지우면 안 되니까 버림
export function clearPendingLogout() {
  setPendingLogout(null);
}

// refreshToken 쿠키로 서버 로그아웃, 실패하면 이유를 표시로 남김
async function sendLogout() {
  let failure: PendingLogout | null = null;

  try {
    await apiFetch<null>("/auth/logout", { method: "POST" });
  } catch (error) {
    if (
        (error instanceof ApiError || error instanceof UnexpectedResponseError) &&
        error.status === 403
    ) {
      failure = "forbidden";
    } else {
      // 서버 꺼짐(TypeError)·5xx 말고 예상 못한 건 원인 확인용으로 콘솔에 남김
      if (
          !(error instanceof ApiError) &&
          !(error instanceof UnexpectedResponseError) &&
          !(error instanceof TypeError)
      ) {
        console.error(error);
      }
      failure = "unconfirmed";
    }
  }

  // 기다리는 동안 다른 탭에서 로그인했으면 그 로그인을 지우는 다시 시도가 생기면 안 되니까 표시 안 남김
  setPendingLogout(failure && !getToken() ? failure : null);
}

// 로그아웃 버튼에서 씀, 화면 이동은 부르는 쪽에서 함
export async function logout() {
  // 토큰부터 지워서 기다리는 동안 늦게 온 재발급 결과도 버려지게 함 (로그아웃 API는 accessToken 필요 없음)
  clearToken();
  await sendLogout();
}

// 로그인 화면의 다시 시도에서 씀
export async function retryLogout() {
  // 다른 탭에서 이미 로그인했으면 쿠키가 새 로그인 거라 부르면 그 로그인이 지워짐, 표시만 버림
  if (getToken()) {
    clearPendingLogout();
    return;
  }

  await sendLogout();
}
