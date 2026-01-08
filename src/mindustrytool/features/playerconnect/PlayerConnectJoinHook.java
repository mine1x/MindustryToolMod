package mindustrytool.features.playerconnect;

import arc.Core;
import arc.util.Timer;
import arc.util.Log;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import mindustry.gen.Icon;
import mindustry.ui.Styles;
import mindustry.Vars;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

public class PlayerConnectJoinHook {
    private static final Map<Object, Boolean> injected = new WeakHashMap<>();

    public static void install(PlayerConnectFeature feature) {
        // Periodically scan for JoinDialog instances and attach our section once
        Timer.schedule(() -> {
            try {
                if (Vars.ui == null) return;
                // try to get the JoinDialog instance from Vars.ui.join
                Object joinDialog = null;
                try {
                    Field joinField = Vars.ui.getClass().getDeclaredField("join");
                    joinField.setAccessible(true);
                    joinDialog = joinField.get(Vars.ui);
                } catch (Throwable ignored) {
                }
                if (joinDialog == null) return;
                Class<?> joinCls;
                try {
                    joinCls = Class.forName("mindustry.ui.dialogs.JoinDialog");
                } catch (Throwable t) {
                    // core not present
                    return;
                }
                if (!joinCls.isInstance(joinDialog)) return;

                synchronized (injected) {
                    if (injected.containsKey(joinDialog)) return;
                    injected.put(joinDialog, true);
                }

                // Add shown listener and inject immediately if shown
                try {
                    final Object jdRef = joinDialog;
                    final PlayerConnectFeature featRef = feature;
                    Method shownMethod = joinCls.getMethod("shown", Runnable.class);
                    Runnable r = () -> Core.app.post(() -> addSection(jdRef, featRef));
                    shownMethod.invoke(joinDialog, r);

                    // try to inject now as well
                    Core.app.post(() -> addSection(jdRef, featRef));
                } catch (Throwable t) {
                    Log.err(t);
                }
            } catch (Throwable t) {
                Log.err(t);
            }
        }, 0f, 1f);
    }

    private static void addSection(Object joinDialog, PlayerConnectFeature feature) {
        try {
            // reflectively get the 'hosts' table
            Field hostsField = joinDialog.getClass().getDeclaredField("hosts");
            hostsField.setAccessible(true);
            Table hosts = (Table) hostsField.get(joinDialog);
            if (hosts == null) return;


            // compute widths by calling targetWidth() and columns() on joinDialog if available
            float targetWidth = 400f;
            int cols = 1;
            try {
                Method targetWidthMethod = joinDialog.getClass().getDeclaredMethod("targetWidth");
                targetWidthMethod.setAccessible(true);
                Object w = targetWidthMethod.invoke(joinDialog);
                if (w instanceof Number) targetWidth = ((Number) w).floatValue();
            } catch (Throwable ignored) {}
            try {
                Method colsMethod = joinDialog.getClass().getDeclaredMethod("columns");
                colsMethod.setAccessible(true);
                Object c = colsMethod.invoke(joinDialog);
                if (c instanceof Number) cols = ((Number) c).intValue();
            } catch (Throwable ignored) {}

            // Build the section (simple, header + content)
            Table[] headerRef = {null};
            hosts.table(t -> {
                headerRef[0] = t;
                t.add("Player connect").pad(10).growX().left();
                t.button(Icon.downOpen, Styles.emptyi, () -> {
                    // open the Player Connect dialog when icon clicked
                    if (feature.getDialog() != null) feature.getDialog().show();
                }).size(40f).right().padRight(10f);
            }).growX().row();

            hosts.row();
            hosts.image().growX().pad(5).padLeft(10).padRight(10).height(3f).color(mindustry.graphics.Pal.accent);
            hosts.row();

            // content: a simple table with the Open button and description
            hosts.table(t -> {
                t.left().top();
                t.add("Your rooms and available connections").pad(8).left();
                t.row();
                t.button("Open Player Connect", () -> {
                    if (feature.getDialog() != null) feature.getDialog().show();
                }).size(220f, 48f).pad(6).left();
            }).width((targetWidth + 5f) * cols).row();

            hosts.pack();
        } catch (Throwable t) {
            Log.err(t);
        }
    }
}
