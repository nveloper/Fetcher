package node.nveloper.fetcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Fetcher 설정/상태 화면. Material You(프레임워크 동적 색상, API31+) 적용.
 */
public class MainActivity extends Activity {

    private LinearLayout statusBox;
    private EditText trigger;
    private EditText keyword;
    private CheckBox autoOpen;

    // Material You 동적 팔레트
    private int primary, onPrimary, cardBg, cardText, textMain, textMuted, offColor;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        resolveColors();
        KeepAliveService.start(this);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        root.setPadding(pad, dp(40), pad, pad);   // 상단은 상태바 여백 확보
        sv.addView(root);

        // 헤더
        TextView header = new TextView(this);
        header.setText("Fetcher");
        header.setTextSize(28f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(primary);
        root.addView(header);

        TextView sub = new TextView(this);
        sub.setText("카톡 인증번호를 읽어 다시 알려줍니다");
        sub.setTextSize(13f);
        sub.setTextColor(textMuted);
        sub.setPadding(0, dp(2), 0, dp(18));
        root.addView(sub);

        // 상태 카드
        statusBox = new LinearLayout(this);
        statusBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable card = new GradientDrawable();
        card.setColor(cardBg);
        card.setCornerRadius(dp(22));
        statusBox.setBackground(card);
        int cp = dp(18);
        statusBox.setPadding(cp, dp(14), cp, dp(14));
        root.addView(statusBox);

        // 권한
        root.addView(sectionLabel("권한"));
        root.addView(secondaryButton("알림 접근 권한", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        root.addView(secondaryButton("접근성 권한", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(secondaryButton("배터리 최적화 제외", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }));

        // 감지 설정
        root.addView(sectionLabel("감지 설정"));

        root.addView(fieldLabel("트리거 문구  ( ; 로 구분 )"));
        trigger = field(Prefs.triggerPhrases(this));
        root.addView(trigger);

        root.addView(fieldLabel("인증 감지 키워드"));
        keyword = field(Prefs.keyword(this));
        root.addView(keyword);

        autoOpen = new CheckBox(this);
        autoOpen.setText("알림의 바로가기로 대화방 자동 열기");
        autoOpen.setTextColor(textMain);
        autoOpen.setChecked(Prefs.autoOpen(this));
        LinearLayout.LayoutParams clp = rowParams();
        clp.topMargin = dp(10);
        autoOpen.setLayoutParams(clp);
        root.addView(autoOpen);

        Button save = primaryButton("저장", v -> {
            Prefs.save(this,
                    trigger.getText().toString(),
                    keyword.getText().toString(),
                    autoOpen.isChecked(),
                    Prefs.pressBack(this));
            Toast.makeText(this, "저장됨", Toast.LENGTH_SHORT).show();
        });
        ((LinearLayout.LayoutParams) save.getLayoutParams()).topMargin = dp(18);
        root.addView(save);

        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        statusBox.removeAllViews();
        statusBox.addView(statusRow("알림 접근", isNotificationAccessEnabled()));
        statusBox.addView(statusRow("접근성", isAccessibilityEnabled()));
    }

    // ---- 조각 위젯 ----

    private View statusRow(String label, boolean ok) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(11f);
        dot.setTextColor(ok ? primary : offColor);
        dot.setPadding(0, 0, dp(10), 0);
        row.addView(dot);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(15f);
        name.setTextColor(cardText);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        TextView state = new TextView(this);
        state.setText(ok ? "켜짐" : "꺼짐 · 탭하여 설정");
        state.setTextSize(14f);
        state.setTextColor(ok ? primary : offColor);
        row.addView(state);

        if (!ok) {
            row.setOnClickListener(v -> startActivity(new Intent(
                    label.equals("접근성") ? Settings.ACTION_ACCESSIBILITY_SETTINGS
                            : Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }
        return row;
    }

    private TextView sectionLabel(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(13f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(textMuted);
        tv.setPadding(dp(4), dp(22), 0, dp(8));
        return tv;
    }

    private TextView fieldLabel(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(13f);
        tv.setTextColor(textMain);
        tv.setPadding(dp(4), dp(10), 0, dp(4));
        return tv;
    }

    private EditText field(String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(textMain);
        e.setTextSize(15f);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBg);
        bg.setCornerRadius(dp(14));
        e.setBackground(bg);
        int p = dp(14);
        e.setPadding(p, dp(12), p, dp(12));
        e.setLayoutParams(rowParams());
        return e;
    }

    private Button primaryButton(String text, View.OnClickListener l) {
        return styledButton(text, primary, onPrimary, l);
    }

    private Button secondaryButton(String text, View.OnClickListener l) {
        return styledButton(text, cardBg, cardText, l);
    }

    private Button styledButton(String text, int bg, int fg, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextSize(15f);
        btn.setTextColor(fg);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(18));
        btn.setBackground(g);
        btn.setOnClickListener(l);
        LinearLayout.LayoutParams lp = rowParams();
        lp.topMargin = dp(8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ---- Material You 색상 ----

    private void resolveColors() {
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            primary   = sys("accent1", 200, 0xFF9FC0FF);
            onPrimary = sys("accent1", 900, 0xFF00315C);
            cardBg    = sys("neutral2", 800, 0xFF26292E);
            cardText  = sys("neutral1", 50, 0xFFE6E6E6);
            textMain  = sys("neutral1", 50, 0xFFE6E6E6);
            textMuted = sys("neutral2", 300, 0xFFB7BCC2);
            offColor  = sys("neutral2", 400, 0xFF9AA0A6);
        } else {
            primary   = sys("accent1", 600, 0xFF2D5BB8);
            onPrimary = 0xFFFFFFFF;
            cardBg    = sys("neutral2", 100, 0xFFEEF1F6);
            cardText  = sys("neutral1", 900, 0xFF1A1C1E);
            textMain  = sys("neutral1", 900, 0xFF1A1C1E);
            textMuted = sys("neutral2", 600, 0xFF5B6069);
            offColor  = sys("neutral2", 500, 0xFF7A808A);
        }
    }

    private int sys(String group, int tone, int fallback) {
        if (Build.VERSION.SDK_INT >= 31) {
            int id = getResources().getIdentifier("system_" + group + "_" + tone, "color", "android");
            if (id != 0) {
                try { return getColor(id); } catch (Exception ignore) { }
            }
        }
        return fallback;
    }

    private boolean isAccessibilityEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(flat)) return false;
        String me = getPackageName() + "/" + OtpAccessibilityService.class.getName();
        String meShort = getPackageName() + "/.OtpAccessibilityService";
        return flat.contains(me) || flat.contains(meShort);
    }

    private boolean isNotificationAccessEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");
        if (TextUtils.isEmpty(flat)) return false;
        return flat.contains(getPackageName());
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
