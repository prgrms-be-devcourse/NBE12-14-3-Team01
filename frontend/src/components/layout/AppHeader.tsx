"use client";

import {useEffect, useState} from "react";
import {useParams, useRouter} from "next/navigation";
import {useCurrentUser} from "@/components/providers/CurrentUserProvider";
import {apiFetch} from "@/lib/api";
import {logout} from "@/lib/logout";

type WorkplaceRole = "MANAGER" | "EMPLOYEE";

type MyWorkplaceResponse = {
  workplaceId: number;
  name: string;
  role: WorkplaceRole;
};

type NotificationResponse = {
  notificationId: number;
  workplaceId: number;
  workplaceName: string;
  type: string;
  message: string;
  createdAt: string;
  readAt: string | null;
};

type NotificationReadResponse = {
  notificationId: number;
  readAt: string;
};

type NotificationLoadStatus =
  | "loading"
  | "success"
  | "error";

async function getNotifications() {
  return await apiFetch<NotificationResponse[]>("/notifications");
}

async function readNotification(notificationId: number) {
  return await apiFetch<NotificationReadResponse>(
    `/notifications/${notificationId}/read`,
    {
      method: "PATCH",
    }
  );
}

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

function parseAsKst(iso: string): Date {
  return new Date(`${iso}+09:00`);
}

function toKstDisplay(iso: string): Date {
  return new Date(parseAsKst(iso).getTime() + KST_OFFSET_MS);
}

function formatNotificationTime(iso: string): string {
  const date = toKstDisplay(iso);

  const month = date.getUTCMonth() + 1;
  const day = date.getUTCDate();
  const hour = String(date.getUTCHours()).padStart(2, "0");
  const minute = String(date.getUTCMinutes()).padStart(2, "0");

  return `${month}월 ${day}일 ${hour}:${minute}`;
}

export default function AppHeader() {
  const router = useRouter();
  const params = useParams();

  const workplaceId = params.workplaceId as string;

  type WorkplaceStatus = "loading" | "success" | "error" | "not-found";

  const [workplaceState, setWorkplaceState] = useState<{
    workplaceId: string;
    name: string;
    role: WorkplaceRole | null;
    status: WorkplaceStatus;
  }>({
    workplaceId,
    name: "",
    role: null,
    status: "loading",
  });

  const isCurrentWorkplace =
      workplaceState.workplaceId === workplaceId;

  const workplaceStatus =
      isCurrentWorkplace ? workplaceState.status : "loading";

  const workplaceName =
      isCurrentWorkplace && workplaceState.status === "success"
          ? workplaceState.name
          : "";

  const workplaceRole =
      isCurrentWorkplace && workplaceState.status === "success"
          ? workplaceState.role
          : null;

  useEffect(() => {
    let cancelled = false;

    const fetchWorkplace = async () => {
      if (!workplaceId) return;

      try {
        const workplaces =
            await apiFetch<MyWorkplaceResponse[]>("/workplaces");

        if (cancelled) return;

        const currentWorkplace = workplaces.find(
            (workplace) =>
                workplace.workplaceId === Number(workplaceId)
        );

        if (!currentWorkplace) {
          setWorkplaceState({
            workplaceId,
            name: "",
            role: null,
            status: "not-found",
          });
          return;
        }

        setWorkplaceState({
          workplaceId,
          name: currentWorkplace.name,
          role: currentWorkplace.role,
          status: "success",
        });
      } catch (error) {
        if (cancelled) return;

        console.error(error);

        setWorkplaceState({
          workplaceId,
          name: "",
          role: null,
          status: "error",
        });
      }
    };

    void fetchWorkplace();

    return () => {
      cancelled = true;
    };
  }, [workplaceId]);

  const workplaceRoleLabel =
      workplaceRole === "MANAGER"
          ? "관리자"
          : workplaceRole === "EMPLOYEE"
              ? "직원"
              : "";

  // 로그인한 사람 이름, 동그라미엔 첫 글자
  const {user, status} = useCurrentUser();
  const userName = status === "success" && user ? user.name.trim() : "";
  const userInitial = userName.charAt(0);

  const [showProfileMenu, setShowProfileMenu] = useState(false);
  const [showNotificationMenu, setShowNotificationMenu] = useState(false);
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [notificationLoadStatus, setNotificationLoadStatus] = useState<NotificationLoadStatus>("loading");
  const [readingNotificationId, setReadingNotificationId] = useState<number | null>(null);
  const [notificationReloadKey, setNotificationReloadKey] = useState(0);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const unreadCount = notifications.filter(
    (notification) => notification.readAt === null
  ).length;

  useEffect(() => {
    let cancelled = false;

    const fetchNotifications = async () => {
      setNotificationLoadStatus("loading");

      try {
        const result = await getNotifications();

        if (cancelled) {
          return;
        }

        setNotifications(result);
        setNotificationLoadStatus("success");
      } catch (error) {
        if (cancelled) {
          return;
        }

        console.error(error);

        setNotifications([]);
        setNotificationLoadStatus("error");
      }
    };

    void fetchNotifications();

    return () => {
      cancelled = true;
    };
  }, [notificationReloadKey]);

  // 서버 로그아웃 후 로그인 화면으로 (실패해도 이동, 안내는 로그인 화면이 보여줌. 뒤로 가기로 못 돌아오게 replace)
  const handleLogout = async () => {
    if (isLoggingOut) {
      return;
    }

    setIsLoggingOut(true);
    await logout();
    router.replace("/login");
  };

  const reloadNotifications = () => {
    if (
      readingNotificationId !== null ||
      notificationLoadStatus === "loading"
    ) {
      return;
    }

    setNotificationLoadStatus("loading");
    setNotificationReloadKey((prev) => prev + 1);
  };

  const handleNotificationClick = async (
    notification: NotificationResponse
  ) => {
    if (
      notification.readAt !== null ||
      readingNotificationId !== null ||
      notificationLoadStatus === "loading"
    ) {
      return;
    }

    setReadingNotificationId(notification.notificationId);

    try {
      const result = await readNotification(
        notification.notificationId
      );

      setNotifications((prev) =>
        prev.map((item) =>
          item.notificationId === result.notificationId
            ? {
                ...item,
                readAt: result.readAt,
              }
            : item
        )
      );
    } catch (error) {
      console.error(error);

      window.alert(
        "알림을 읽음 처리하지 못했습니다. 다시 시도해주세요."
      );
    } finally {
      setReadingNotificationId(null);
    }
  };

  const handleNotificationMenuToggle = () => {
    const nextOpen = !showNotificationMenu;

    setShowNotificationMenu(nextOpen);
    setShowProfileMenu(false);

    if (nextOpen) {
      reloadNotifications();
    }
  };

  return (
      <header
          className="relative z-40 flex h-[76px] items-center justify-between border-b border-[#dce8e2] bg-white pl-20 pr-4 sm:pr-6 lg:px-8">
        {/* 현재 근무지 */}
        <div>
          <p className="text-xs font-semibold text-[#78847f]">
            현재 근무지
          </p>

          <p className="mt-0.5 font-black">
            {workplaceStatus === "success"
                ? workplaceName
                : workplaceStatus === "loading"
                    ? "확인 중..."
                    : workplaceStatus === "not-found"
                        ? "소속 근무지 없음"
                        : "근무지 조회 실패"}
          </p>
        </div>

        {/* 우측 */}
        <div className="flex items-center gap-3 sm:gap-5">
          {/* 알림 */}
          <div className="relative">
            <button
                type="button"
                onClick={handleNotificationMenuToggle}
                className="relative grid h-10 w-10 place-items-center rounded-xl text-lg transition hover:bg-[#f3fbf7]"
                aria-label="알림"
            >
              🔔

              {unreadCount > 0 && (
                  <span
                      className="absolute right-0.5 top-0.5 grid min-h-[16px] min-w-[16px] place-items-center rounded-full bg-[#d95555] px-1 text-[10px] font-bold leading-none text-white">
                {unreadCount}
              </span>
              )}
            </button>

            {/* 알림 메뉴 */}
            {showNotificationMenu && (
                <div
                    className="absolute right-0 top-[calc(100%+10px)] z-50 w-[340px] max-w-[calc(100vw-24px)] overflow-hidden rounded-2xl border border-[#dce8e2] bg-white shadow-xl">
                  <div
                      className="flex items-center justify-between border-b border-[#edf2ef] px-4 py-4">
                    <div className="flex items-center gap-2">
                      <p className="font-black">
                        알림
                      </p>

                      {unreadCount > 0 && (
                          <span
                              className="rounded-full bg-[#dff7ec] px-2 py-0.5 text-xs font-bold text-[#14956c]">
                      {unreadCount}
                    </span>
                      )}
                    </div>

                  </div>

                  <div className="max-h-[360px] overflow-y-auto">
                    {notificationLoadStatus === "loading" ? (
                      <div className="px-4 py-10 text-center">
                        <p className="text-sm font-bold text-[#66736d]">
                          알림을 불러오는 중입니다...
                        </p>
                      </div>
                    ) : notificationLoadStatus === "error" ? (
                      <div className="px-4 py-10 text-center">
                        <p className="text-sm font-bold text-[#d95555]">
                          알림을 불러오지 못했습니다.
                        </p>

                        <button
                          type="button"
                          onClick={reloadNotifications}
                          className="mt-3 text-xs font-bold text-[#14956c] hover:underline"
                          >
                            다시 시도
                          </button>
                        </div>
                    ) : notifications.length === 0 ? (
                       <div className="px-4 py-10 text-center">
                         <p className="text-sm font-bold text-[#66736d]">
                          새로운 알림이 없어요.
                         </p>
                        </div>
                      ) : (
                        notifications.map((notification) => (
                          <button
                            key={notification.notificationId}
                            type="button"
                            onClick={() => handleNotificationClick(notification)}
                            disabled={
                              notification.readAt !== null ||
                              readingNotificationId !== null
                            }
                            className={`flex w-full gap-3 border-b border-[#edf2ef] px-4 py-4 text-left transition last:border-b-0 ${
                              notification.readAt !== null
                                ? "cursor-default bg-white"
                                : "cursor-pointer bg-[#f7fcf9] hover:bg-[#eef8f3]"
                            }`}
                        >
                          <div className="pt-2">
                            <span
                              className={`block h-2 w-2 rounded-full ${
                                notification.readAt !== null
                                  ? "bg-transparent"
                                  : "bg-[#14956c]"
                              }`}
                            />
                          </div>

                          <div className="min-w-0 flex-1">
                            <p className="text-xs font-semibold text-[#78847f]">
                              {notification.workplaceName}
                            </p>

                            <p
                              className={`mt-1 text-sm leading-6 ${
                                notification.readAt !== null
                                  ? "font-medium text-[#66736d]"
                                  : "font-bold text-[#1f2925]"
                              }`}
                            >
                              {notification.message}
                            </p>

                            <p className="mt-1 text-xs text-[#9aa5a0]">
                              {formatNotificationTime(notification.createdAt)}
                            </p>
                          </div>
                        </button>
                     ))
                    )}
                  </div>
                </div>
            )}
          </div>

          {/* 프로필 */}
          <div className="relative">
            <button
                type="button"
                onClick={() => {
                  setShowProfileMenu((prev) => !prev);
                  setShowNotificationMenu(false);
                }}
                className="flex items-center gap-3 rounded-xl px-2 py-1.5 text-left transition hover:bg-[#f3fbf7]"
            >
              <div
                  className="grid h-10 w-10 place-items-center rounded-full bg-[#dff7ec] font-black text-[#005642]">
                {userInitial}
              </div>

              <div className="hidden min-w-0 sm:block">
                {/* 실패하면 빈칸 (높이는 유지) */}
                <p className="min-h-5 max-w-[160px] truncate text-sm font-black">
                  {userName}
                </p>

                <p className="mt-0.5 text-xs text-[#78847f]">
                  {workplaceStatus === "success"
                      ? `${workplaceRoleLabel} · ${workplaceName}`
                      : workplaceStatus === "loading"
                          ? "근무지 확인 중"
                          : workplaceStatus === "not-found"
                              ? "소속 근무지 없음"
                              : "근무지 조회 실패"}
                </p>
              </div>

              <span
                  className={`hidden text-xs text-[#78847f] transition-transform sm:block ${
                      showProfileMenu ? "rotate-180" : ""
                  }`}
              >
              ▾
            </span>
            </button>

            {/* 프로필 메뉴 */}
            {showProfileMenu && (
                <div
                    className="absolute right-0 top-[calc(100%+8px)] z-50 w-52 overflow-hidden rounded-xl border border-[#dce8e2] bg-white shadow-lg">
                  <div className="border-b border-[#edf2ef] px-4 py-3">
                    <p className="min-h-5 truncate text-sm font-black">
                      {userName}
                    </p>

                    <p className="mt-0.5 text-xs text-[#78847f]">
                      {workplaceStatus === "success"
                          ? `${workplaceRoleLabel} · ${workplaceName}`
                          : workplaceStatus === "loading"
                              ? "근무지 확인 중"
                              : workplaceStatus === "not-found"
                                  ? "소속 근무지 없음"
                                  : "근무지 조회 실패"}
                    </p>
                  </div>

                  <button
                      type="button"
                      onClick={handleLogout}
                      disabled={isLoggingOut}
                      className="w-full px-4 py-3 text-left text-sm font-bold text-[#d95555] transition hover:bg-[#fff5f5] disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {isLoggingOut ? "로그아웃 중..." : "로그아웃"}
                  </button>
                </div>
            )}
          </div>
        </div>
      </header>
  );
}