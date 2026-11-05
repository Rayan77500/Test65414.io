package com.rayan.w204dash.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rayan.w204dash.media.MediaHub;

/** Widget musique : pochette, titre, artiste, precedent / lecture-pause / suivant. */
public class MusicWidget extends LinearLayout implements MediaHub.Listener {

    private final MediaHub hub;
    private final ImageView art;
    private final IconView noteIcon;
    private final TextView title;
    private final TextView artist;
    private final IconView playIcon;
    private final TextView hint;

    public MusicWidget(Context c, MediaHub hub, final Runnable askNotificationAccess) {
        super(c);
        this.hub = hub;
        setOrientation(VERTICAL);
        setBackgroundDrawable(Ui.glass(22));
        int p = Ui.dp(14);
        setPadding(p, p, p, p);

        LinearLayout top = Ui.row(c);
        FrameLayout artBox = new FrameLayout(c);
        artBox.setBackgroundDrawable(Ui.rounded(Theme.withAlpha(Theme.accent, 0x30), 0, 12));
        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setVisibility(GONE);
        noteIcon = new IconView(c, Icons.MUSIC, Theme.accent);
        int ip = Ui.dp(12);
        noteIcon.setPadding(ip, ip, ip, ip);
        artBox.addView(noteIcon, new FrameLayout.LayoutParams(-1, -1));
        artBox.addView(art, new FrameLayout.LayoutParams(-1, -1));
        top.addView(artBox, new LayoutParams(Ui.dp(58), Ui.dp(58)));

        LinearLayout texts = Ui.column(c);
        title = Ui.text(c, "Musique", 18, Theme.text, Ui.MEDIUM);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        artist = Ui.text(c, "Touchez lecture pour démarrer", 14, Theme.text2, Ui.REGULAR);
        artist.setSingleLine(true);
        artist.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        LayoutParams alp = new LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(6);
        texts.addView(artist, alp);
        LayoutParams tlp = new LayoutParams(0, -2, 1);
        tlp.leftMargin = Ui.dp(12);
        top.addView(texts, tlp);
        addView(top, new LayoutParams(-1, -2));

        LinearLayout ctrl = Ui.row(c);
        ctrl.setGravity(Gravity.CENTER);
        ctrl.addView(control(c, Icons.PREV, false, new OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicWidget.this.hub.previous();
            }
        }), new LayoutParams(0, Ui.dp(58), 1));
        View play = control(c, Icons.PLAY, true, new OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicWidget.this.hub.playPause();
            }
        });
        playIcon = (IconView) ((FrameLayout) play).getChildAt(0);
        LayoutParams plp = new LayoutParams(Ui.dp(70), Ui.dp(70));
        plp.leftMargin = Ui.dp(10);
        plp.rightMargin = Ui.dp(10);
        ctrl.addView(play, plp);
        ctrl.addView(control(c, Icons.NEXT, false, new OnClickListener() {
            @Override
            public void onClick(View v) {
                MusicWidget.this.hub.next();
            }
        }), new LayoutParams(0, Ui.dp(58), 1));
        LayoutParams clp = new LayoutParams(-1, -2);
        clp.topMargin = Ui.dp(12);
        addView(ctrl, clp);

        hint = Ui.text(c, "Afficher les titres : autoriser l'accès aux notifications ›", 13, Theme.accent, Ui.REGULAR);
        hint.setPadding(0, Ui.dp(10), 0, Ui.dp(2));
        hint.setGravity(Gravity.CENTER);
        hint.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (askNotificationAccess != null) askNotificationAccess.run();
            }
        });
        addView(hint, new LayoutParams(-1, -2));
        refresh();
    }

    private View control(Context c, int icon, boolean main, OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        f.setBackgroundDrawable(main ? Ui.rounded(Theme.accent, 0, 40) : Ui.pressable(30, 0x00000000));
        if (main) {
            android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
            s.addState(new int[]{android.R.attr.state_pressed}, Ui.rounded(Theme.withAlpha(Theme.accent, 0xB0), 0, 40));
            s.addState(new int[]{}, Ui.rounded(Theme.accent, 0, 40));
            f.setBackgroundDrawable(s);
        }
        IconView iv = new IconView(c, icon, main ? 0xFF0B0F17 : Theme.text);
        int p = Ui.dp(main ? 20 : 16);
        iv.setPadding(p, p, p, p);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        f.setClickable(true);
        f.setOnClickListener(l);
        return f;
    }

    private void refresh() {
        String t = hub.title();
        if (t != null) {
            title.setText(t);
            String a = hub.artist();
            String app = hub.app();
            artist.setText(a != null ? a + (app != null ? "  ·  " + app : "") : (app != null ? app : ""));
        } else {
            title.setText("Musique");
            artist.setText("Touchez lecture pour démarrer");
        }
        playIcon.setIcon(hub.playing() == 1 ? Icons.PAUSE : Icons.PLAY);
        Bitmap b = hub.art();
        if (b != null) {
            art.setImageBitmap(b);
            art.setVisibility(VISIBLE);
        } else {
            art.setImageDrawable(null);
            art.setVisibility(GONE);
        }
        hint.setVisibility(hub.canShowTitles() && !hub.hasNotificationAccess() ? VISIBLE : GONE);
    }

    @Override
    public void onMediaChanged(MediaHub h) {
        refresh();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        hub.addListener(this);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        hub.removeListener(this);
        super.onDetachedFromWindow();
    }

    public void onResumeCheck() {
        if (hub.hasNotificationAccess()) hub.refreshSessions();
        refresh();
    }
}
