package node.nveloper.fetcher;

/**
 * 알림 리스너 <-> 접근성 서비스 사이의 공유 상태.
 * 트리거가 들어오면 '무장(armed)' 상태가 되고, 접근성 서비스는 무장 중일 때만 화면을 읽는다.
 */
public final class AppState {
    private AppState() {}

    /** 이 시각 이전까지만 화면을 읽는다. 0이면 무장 해제. */
    public static volatile long armedUntil = 0L;

    /** 트리거를 띄운 카톡 알림의 제목(채널명 추정) - 재게시 알림 라벨 보조용. */
    public static volatile String lastSourceTitle = "";

    /** 직전 contentIntent 발사 시각 - 중복 열기 방지(디바운스). */
    public static volatile long lastOpenAt = 0L;

    /** 직전에 재게시한 코드/시각 - 같은 코드 중복 재게시 방지. */
    public static volatile String lastPostedCode = "";
    public static volatile long lastPostedAt = 0L;

    public static void arm(long windowMs, String sourceTitle) {
        armedUntil = System.currentTimeMillis() + windowMs;
        if (sourceTitle != null) lastSourceTitle = sourceTitle;
    }

    public static void disarm() {
        armedUntil = 0L;
    }

    public static boolean isArmed() {
        return System.currentTimeMillis() < armedUntil;
    }
}
