package articulect;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Follows the OS light/dark setting (polled every few seconds). */
final class Theme {
    private Theme() {}

    static void install() {
        boolean dark = isDark();
        apply(dark);
        AtomicBoolean last = new AtomicBoolean(dark);
        ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "theme-watch");
            t.setDaemon(true);
            return t;
        });
        ex.scheduleWithFixedDelay(() -> {
            boolean d = isDark();
            if (d != last.getAndSet(d))
                SwingUtilities.invokeLater(() -> { apply(d); FlatLaf.updateUI(); });
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static void apply(boolean dark) {
        FlatLaf.setup(dark ? new FlatDarkLaf() : new FlatLightLaf());
    }

    static boolean isDark() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac"))
            return run("defaults", "read", "-g", "AppleInterfaceStyle").toLowerCase(Locale.ROOT).contains("dark");
        if (os.contains("win"))
            return run("reg", "query",
                    "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme").contains("0x0");
        String scheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").toLowerCase(Locale.ROOT);
        if (scheme.contains("dark")) return true;
        if (scheme.contains("default") || scheme.contains("light")) return false;
        return run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme").toLowerCase(Locale.ROOT).contains("dark");
    }

    private static String run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor(2, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) {
            return "";
        }
    }
}
