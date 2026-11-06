package node.nveloper.fetcher;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * 카카오톡 알림을 감지하는 역할만 한다.
 * 설정된 트리거 문구가 들어 있으면:
 *   1) 공유 상태를 '무장'시키고(5분 창),
 *   2) 그 알림의 contentIntent를 실행해 해당 대화방을 연다.
 * 실제 인증번호 추출/재게시는 OtpAccessibilityService가 한다.
 */
public class KakaoNotificationListener extends NotificationListenerService {

    private static final String TAG = "Fetcher/Listener";
    private static final String KAKAO = "com.kakao.talk";
    private static final long OPEN_DEBOUNCE_MS = 2500L;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || !KAKAO.equals(sbn.getPackageName())) return;

            Notification n = sbn.getNotification();
            if (n == null) return;

            String haystack = extractText(n);
            if (!matchesTrigger(haystack)) return;

            String title = titleOf(n);

            // 무장: 이제부터 windowMs 안에 '인증' 화면이 잡히면 접근성 서비스가 재게시한다.
            AppState.arm(Prefs.windowMs(this), title);

            if (Prefs.autoOpen(this)) {
                long now = System.currentTimeMillis();
                if (now - AppState.lastOpenAt < OPEN_DEBOUNCE_MS) return;
                AppState.lastOpenAt = now;
                PendingIntent ci = n.contentIntent;
                if (ci != null) {
                    try {
                        ci.send(this, 0, null, null, null, null, balOptions());
                    } catch (PendingIntent.CanceledException e) {
                        Log.w(TAG, "contentIntent canceled", e);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "onNotificationPosted error", t);
        }
    }

    /** 백그라운드에서 PendingIntent로 액티비티를 띄우도록 발신자 측 BAL 허용 옵션. */
    private static Bundle balOptions() {
        try {
            ActivityOptions ao = ActivityOptions.makeBasic();
            if (Build.VERSION.SDK_INT >= 34) {
                ao.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
            }
            return ao.toBundle();
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean matchesTrigger(String haystack) {
        if (haystack == null) return false;
        String[] phrases = Prefs.triggerPhraseList(this);
        if (phrases.length == 0) return true; // 비워두면 모든 카톡 알림이 트리거
        for (String p : phrases) {
            if (haystack.contains(p)) return true;
        }
        return false;
    }

    private static String extractText(Notification n) {
        Bundle ex = n.extras;
        StringBuilder sb = new StringBuilder();
        if (ex != null) {
            append(sb, ex.getCharSequence(Notification.EXTRA_TITLE));
            append(sb, ex.getCharSequence(Notification.EXTRA_TEXT));
            append(sb, ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            append(sb, ex.getCharSequence(Notification.EXTRA_SUB_TEXT));
            CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                for (CharSequence l : lines) append(sb, l);
            }
        }
        if (n.tickerText != null) append(sb, n.tickerText);
        return sb.toString();
    }

    private static String titleOf(Notification n) {
        Bundle ex = n.extras;
        if (ex == null) return "";
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        return t == null ? "" : t.toString();
    }

    private static void append(StringBuilder sb, CharSequence cs) {
        if (cs != null) {
            sb.append(cs).append('\n');
        }
    }
}
