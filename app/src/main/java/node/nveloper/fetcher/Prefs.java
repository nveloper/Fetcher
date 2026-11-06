package node.nveloper.fetcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 설정값. 기본값은 지금까지 확인한 카톡 알림톡 화면에 맞춰 둠.
 * - triggerPhrases: 이 문구가 카톡 알림(제목/본문)에 들어 있으면 방을 연다. ';'로 구분.
 * - keyword: 열린 화면에서 이 글자가 들어간 말풍선만 인증번호로 간주.
 * - windowMs: 트리거 후 이 시간 안에 keyword 화면이 안 잡히면 아무것도 안 함.
 */
public final class Prefs {
    private static final String FILE = "fetcher_prefs";
    private static final String K_TRIGGER = "trigger_phrases";
    private static final String K_KEYWORD = "keyword";
    private static final String K_WINDOW = "window_ms";
    private static final String K_AUTO_OPEN = "auto_open";
    private static final String K_PRESS_BACK = "press_back";

    public static final String DEF_TRIGGER = "메시지가 도착;알림이 왔습니다;알림톡";
    public static final String DEF_KEYWORD = "인증";
    public static final long DEF_WINDOW = 5 * 60 * 1000L;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String triggerPhrases(Context c) {
        return sp(c).getString(K_TRIGGER, DEF_TRIGGER);
    }

    public static String[] triggerPhraseList(Context c) {
        String raw = triggerPhrases(c).trim();
        if (raw.isEmpty()) return new String[0];
        String[] parts = raw.split(";");
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.toArray(new String[0]);
    }

    public static String keyword(Context c) {
        String k = sp(c).getString(K_KEYWORD, DEF_KEYWORD).trim();
        return k.isEmpty() ? DEF_KEYWORD : k;
    }

    public static long windowMs(Context c) {
        return sp(c).getLong(K_WINDOW, DEF_WINDOW);
    }

    public static boolean autoOpen(Context c) {
        return sp(c).getBoolean(K_AUTO_OPEN, true);
    }

    public static boolean pressBack(Context c) {
        return sp(c).getBoolean(K_PRESS_BACK, true);
    }

    public static void save(Context c, String trigger, String keyword,
                            boolean autoOpen, boolean pressBack) {
        sp(c).edit()
                .putString(K_TRIGGER, trigger)
                .putString(K_KEYWORD, keyword)
                .putBoolean(K_AUTO_OPEN, autoOpen)
                .putBoolean(K_PRESS_BACK, pressBack)
                .apply();
    }
}
