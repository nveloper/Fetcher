package node.nveloper.fetcher;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 무장(armed) 상태일 때 카카오톡 화면을 읽어,
 * keyword('인증')가 들어간 말풍선에서 4~8자리 숫자를 뽑아 재게시한다.
 *
 * 핵심:
 *  - 방이 열려 내용이 바뀔 때마다 읽기를 '지연 예약'해, 스크롤/렌더가 끝난 뒤
 *    (최신 말풍선이 맨 아래에 자리잡은 뒤) 읽는다. -> '한 박자 밀림' 방지.
 *  - 과거 기록은 올리지 않는다. 오직 '맨 아래 = 방금 온 것' 한 개만 재게시.
 *  - 올린 뒤에도 창(5분) 동안 방에 머물며, 뒤따라 오는(알림조차 없는) 인증도 최신만 계속 잡는다.
 *  - 코드별 10분 중복 방지.
 */
public class OtpAccessibilityService extends AccessibilityService {

    private static final String TAG = "Fetcher/Acc";
    private static final String KAKAO = "com.kakao.talk";
    private static final String CHANNEL_ID = "fetcher_otp";
    private static final String TOOLBAR_TITLE_ID = "com.kakao.talk:id/toolbar_default_title_text";

    private static final long SETTLE_MS = 1200;     // 내용 변화가 멎고 이만큼 뒤에 읽는다
    private static final long DEDUPE_MS = 10 * 60 * 1000L;

    private static final Pattern CODE = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)");

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Long> recentlyPosted = new HashMap<>();
    private Runnable pendingRead;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        ensureChannel();
        KeepAliveService.start(this);   // 절전에 지지 않게 상주 서비스 가동
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            if (!AppState.isArmed()) return;
            if (event == null) return;
            CharSequence pkg = event.getPackageName();
            if (pkg == null || !KAKAO.equals(pkg.toString())) return;

            // 내용이 계속 바뀌는 동안은 읽기를 뒤로 미룬다(디바운스). 변화가 멎으면 그때 읽음.
            if (pendingRead != null) main.removeCallbacks(pendingRead);
            pendingRead = this::readAndPost;
            main.postDelayed(pendingRead, SETTLE_MS);
        } catch (Throwable t) {
            Log.e(TAG, "onAccessibilityEvent error", t);
        }
    }

    private void readAndPost() {
        try {
            if (!AppState.isArmed()) return;

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            CharSequence pkg = root.getPackageName();
            if (pkg == null || !KAKAO.equals(pkg.toString())) return;

            String keyword = Prefs.keyword(this);
            List<Cand> cands = new ArrayList<>();
            collect(root, keyword, cands, 0);
            if (cands.isEmpty()) return;    // 아직 '인증' 화면 아님 -> 다음 이벤트 때 다시

            // 맨 아래 = 방금 온 것 한 개만. (bounds.bottom이 가장 큰 것, 동률이면 문서 순서상 뒤엣것)
            Cand newest = cands.get(0);
            for (int i = 1; i < cands.size(); i++) {
                Cand c = cands.get(i);
                if (c.bottom >= newest.bottom) newest = c;
            }

            String code = extractCode(newest.text);
            if (code == null) return;

            long now = System.currentTimeMillis();
            purge(now);
            if (recentlyPosted.containsKey(code)) return;  // 이미 올린 코드 -> 무시(창은 유지)

            String source = readToolbarTitle(root);
            if (source == null || source.isEmpty()) source = AppState.lastSourceTitle;

            postOtp(code, source);
            recentlyPosted.put(code, now);
            AppState.lastPostedCode = code;
            AppState.lastPostedAt = now;
            // 방을 나가지 않고 창(5분) 동안 머물며 뒤따라 오는 인증도 최신만 계속 잡는다.
        } catch (Throwable t) {
            Log.e(TAG, "readAndPost error", t);
        }
    }

    @Override
    public void onInterrupt() { }

    private void collect(AccessibilityNodeInfo node, String keyword, List<Cand> out, int depth) {
        if (node == null || depth > 60) return;
        CharSequence t = node.getText();
        if (t == null) t = node.getContentDescription();
        if (t != null) {
            String s = t.toString();
            if (s.contains(keyword)) {
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                out.add(new Cand(s, r.bottom));
            }
        }
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            collect(node.getChild(i), keyword, out, depth + 1);
        }
    }

    private String readToolbarTitle(AccessibilityNodeInfo root) {
        try {
            List<AccessibilityNodeInfo> ns = root.findAccessibilityNodeInfosByViewId(TOOLBAR_TITLE_ID);
            if (ns != null) {
                for (AccessibilityNodeInfo n : ns) {
                    if (n == null) continue;
                    CharSequence t = n.getContentDescription();
                    if (t == null || t.length() == 0) t = n.getText();
                    if (t != null && t.length() > 0) return t.toString();
                }
            }
        } catch (Throwable ignore) { }
        return "";
    }

    private static String extractCode(String text) {
        if (text == null) return null;
        Matcher m = CODE.matcher(text);
        String best = null;
        while (m.find()) {
            String g = m.group(1);
            if (best == null || g.length() >= best.length()) best = g;
        }
        return best;
    }

    private void purge(long now) {
        Iterator<Map.Entry<String, Long>> it = recentlyPosted.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > DEDUPE_MS) it.remove();
        }
    }

    private void ensureChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "인증번호 재게시", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("카톡에서 읽어온 인증번호를 Fetcher 알림으로 띄웁니다.");
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    private void postOtp(String code, String source) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        // 앱 라벨은 이미 "Fetcher"로 표시됨. 제목엔 출처 채널명만, 본문엔 코드만(매크로 파싱 쉽게).
        String title = (source == null || source.isEmpty()) ? "Fetcher" : source;
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText(code)
                .setStyle(new Notification.BigTextStyle().bigText(code))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .build();
        nm.notify(code.hashCode(), n);
        Log.d(TAG, "posted code=" + code + " source=" + source);
    }

    private static final class Cand {
        final String text;
        final int bottom;
        Cand(String text, int bottom) {
            this.text = text;
            this.bottom = bottom;
        }
    }
}
