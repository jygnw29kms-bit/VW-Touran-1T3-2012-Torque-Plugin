package de.growcentral.touranlive;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQ_BT = 10;
    private static final int REQ_EXPORT_LOG = 20;

    private static final int BG = Color.rgb(5, 7, 10);
    private static final int PANEL = Color.rgb(17, 20, 24);
    private static final int PANEL_2 = Color.rgb(27, 31, 36);
    private static final int RED = Color.rgb(205, 28, 38);
    private static final int TEXT = Color.rgb(242, 242, 242);
    private static final int MUTED = Color.rgb(155, 160, 166);
    private static final int OK = Color.rgb(75, 190, 120);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private BluetoothSocket socket;
    private BufferedReader reader;
    private OutputStream writer;
    private volatile boolean polling = false;

    private LinearLayout content;
    private TextView connectionBadge;
    private TextView loggerBadge;
    private TextView statusLine;
    private GridLayout liveGrid;

    private final Object logLock = new Object();
    private final StringBuilder csvLog = new StringBuilder();
    private int logRows = 0;
    private long logStartedAt = 0L;

    private final Map<String, Boolean> support = new LinkedHashMap<>();
    private final Map<String, Double> minSeen = new LinkedHashMap<>();
    private final Map<String, Double> maxSeen = new LinkedHashMap<>();
    private String protocolName = "unbekannt";

    private static class Pid {
        final String cmd, label, unit;
        Pid(String cmd, String label, String unit) {
            this.cmd = cmd; this.label = label; this.unit = unit;
        }
    }

    private static class Launchable {
        final String label, packageName;
        Launchable(String label, String packageName) {
            this.label = label; this.packageName = packageName;
        }
        @Override public String toString() { return label; }
    }

    private final Pid[] pids = new Pid[] {
        new Pid("0104","Motorlast","%"),
        new Pid("0105","Kühlmittel","°C"),
        new Pid("010B","Saugrohrdruck","kPa"),
        new Pid("010C","Drehzahl","rpm"),
        new Pid("010D","Geschwindigkeit","km/h"),
        new Pid("010E","Zündwinkel","°"),
        new Pid("010F","Ansaugluft","°C"),
        new Pid("0110","Luftmasse","g/s"),
        new Pid("0111","Drosselklappe","%"),
        new Pid("012F","Tank","%"),
        new Pid("0133","Umgebungsdruck","kPa"),
        new Pid("0142","ECU-Spannung","V"),
        new Pid("0146","Außentemperatur","°C"),
        new Pid("0149","Pedalstellung","%"),
        new Pid("014C","Drossel Soll","%"),
        new Pid("015C","Öltemperatur","°C"),
        new Pid("015E","Kraftstoffrate","L/h"),
        new Pid("0162","Drehmoment Ist","%"),
        new Pid("0163","Referenzmoment","Nm")
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        resetLog();
        buildShell();
        showHome();
        requestBtPermission();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(18), dp(10), dp(18), dp(8));
        top.setBackgroundColor(Color.rgb(10, 12, 15));

        TextView brand = new TextView(this);
        brand.setText("135er  TOURAN");
        brand.setTextColor(TEXT);
        brand.setTextSize(24);
        brand.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));

        connectionBadge = badge("OBD: getrennt", RED);
        top.addView(connectionBadge);
        loggerBadge = badge("LOG: 0", PANEL_2);
        top.addView(loggerBadge);
        root.addView(top);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(12), dp(18), dp(12));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(10), dp(7), dp(10), dp(9));
        nav.setBackgroundColor(Color.rgb(10, 12, 15));
        nav.addView(navButton("HOME", v -> showHome()), weight());
        nav.addView(navButton("FAHRZEUG", v -> showVehicle()), weight());
        nav.addView(navButton("LOGGER", v -> showLogger()), weight());
        nav.addView(navButton("VCDS", v -> showVcds()), weight());
        nav.addView(navButton("APPS", v -> showApps()), weight());
        nav.addView(navButton("EINSTELLUNGEN", v -> openSettings()), weight());
        root.addView(nav);

        setContentView(root);
    }

    private void showHome() {
        content.removeAllViews();

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(12), dp(8), dp(12), dp(8));

        ImageView car = new ImageView(this);
        car.setImageResource(de.growcentral.touranlive.R.drawable.touran_live_icon);
        car.setAdjustViewBounds(true);
        car.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        hero.addView(car, new LinearLayout.LayoutParams(0, dp(210), 0.42f));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(18), 0, 0, 0);

        TextView title = title("VW TOURAN 1T3");
        info.addView(title);
        TextView sub = body("2012  •  1.4 TSI  •  CAVC  •  B-JU 6969");
        sub.setTextSize(18);
        info.addView(sub);

        statusLine = body("Keine Fahrzeugdaten simuliert. Live-Werte erscheinen erst nach bestätigter ECU-Antwort.");
        statusLine.setTextColor(MUTED);
        statusLine.setPadding(0, dp(10), 0, dp(14));
        info.addView(statusLine);

        LinearLayout connectRow = new LinearLayout(this);
        connectRow.addView(actionButton("OBD VERBINDEN", v -> connect()), weight());
        connectRow.addView(actionButton("TRENNEN", v -> disconnect()), weight());
        info.addView(connectRow);
        hero.addView(info, new LinearLayout.LayoutParams(0, -2, 0.58f));
        content.addView(hero);

        GridLayout quick = new GridLayout(this);
        quick.setColumnCount(4);
        addQuick(quick, "RADIO", () -> openMatchedApp(new String[]{"radio","fm"}, "Radio"));
        addQuick(quick, "TELEFON", this::openPhone);
        addQuick(quick, "CARPLAY", () -> openMatchedApp(new String[]{"zlink","tlink","carlink","carplay","autokit","carletter"}, "CarPlay"));
        addQuick(quick, "NAVIGATION", this::openNavigation);
        content.addView(quick, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showVehicle() {
        content.removeAllViews();

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = title("FAHRZEUG • LIVE");
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(actionButton("VERBINDEN", v -> connect()));
        head.addView(actionButton("TRENNEN", v -> disconnect()));
        content.addView(head);

        TextView note = body("Es werden ausschließlich PIDs angezeigt, die das Motorsteuergerät über den OBD-II-Support-Bitmap bestätigt und anschließend mit gültigen Daten beantwortet.");
        note.setPadding(0, dp(6), 0, dp(8));
        content.addView(note);

        ScrollView scroll = new ScrollView(this);
        liveGrid = new GridLayout(this);
        liveGrid.setColumnCount(4);
        scroll.addView(liveGrid);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        if (!polling) {
            TextView empty = body("Noch keine bestätigten Live-Werte.");
            empty.setTextSize(20);
            empty.setGravity(Gravity.CENTER);
            liveGrid.addView(empty, tileParams());
        }
    }

    private void showLogger() {
        content.removeAllViews();
        TextView t = title("DIAGNOSELOGGER");
        content.addView(t);

        TextView d = body("Der Logger speichert ELM327-Initialisierung, Support-Abfragen, rohe ECU-Antworten, dekodierte Werte und Fehlerzustände. Export erfolgt über Androids Dateiauswahl direkt auf internen Speicher oder USB.");
        d.setPadding(0, dp(8), 0, dp(18));
        content.addView(d);

        TextView count = body("Aktuell: " + logRows + " Logzeilen");
        count.setTextSize(22);
        count.setTextColor(TEXT);
        content.addView(count);

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(18), 0, 0);
        row.addView(actionButton("LOG AUF USB", v -> exportLog()), weight());
        row.addView(actionButton("LOG LEEREN", v -> {
            resetLog();
            showLogger();
            toast("Logger geleert.");
        }), weight());
        content.addView(row);
    }

    private void showVcds() {
        content.removeAllViews();
        content.addView(title("VCDS • ABGLEICH"));

        TextView intro = body("Arbeite die Punkte in dieser Reihenfolge ab. Nichts codieren, keine Anpassungen schreiben und keine Grundeinstellungen starten. Für uns werden ausschließlich Identität, Messwerte und Logs benötigt.");
        intro.setPadding(0, dp(6), 0, dp(10));
        content.addView(intro);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        String[] steps = new String[] {
                "1  AUTO-SCAN\nKompletten Fahrzeug-Auto-Scan ausführen und speichern.",
                "2  01-MOTOR → ERWEITERTE ID\nTeilenummer, Softwarestand, ASAM/ODX-Kennung, Motorsteuergerät-Identität sichern.",
                "3  CONTROLLER CHANNELS MAP → 01-MOTOR\nMesswerte als CSV/PLB erzeugen. Ausgabe vollständig speichern.",
                "4  01-MOTOR → ERWEITERTE MESSWERTE\nLeerlauf-Log mit Drehzahl, Geschwindigkeit, Kühlmittel, Öltemperatur, Ansaugluft, Umgebungsdruck, Saugrohr-/Ladedruck, Luftmasse, Drosselklappe, Pedal, Bordspannung, Zündwinkel, Last und Kraftstoffkorrekturen.",
                "5  LADEDRUCK\nFalls getrennt vorhanden: Soll-Ladedruck und Ist-Ladedruck mitloggen.",
                "6  KRAFTSTOFF\nRaildruck Soll/Ist, Einspritzzeit, Lambda Soll/Ist und Kraftstoffkorrekturen auswählen, soweit VCDS sie für dieses Steuergerät anbietet.",
                "7  ZÜNDUNG / KLOPFREGELUNG\nZündwinkel sowie Klopf-/Zündwinkelrücknahme je Zylinder auswählen, soweit vorhanden.",
                "8  NOCKENWELLE / STEUERZEITEN\nSoll/Ist-Winkel bzw. Anpassungswerte sichern, soweit vorhanden.",
                "9  FEHLZÜNDUNGEN\nMisfire-Zähler Zylinder 1-4 auswählen, soweit vorhanden.",
                "10  ELEKTRIK\nSteuergeräte-/Batteriespannung und Generatorlast bzw. Generatorwerte sichern, soweit vorhanden.",
                "11  LIVE-LOG LEERLAUF\nMotor warm, 2-3 Minuten loggen.",
                "12  LIVE-LOG 2000 RPM\nIm Stand nur falls sicher und zulässig: ca. 30 Sekunden stabil um 2000 rpm loggen.",
                "13  DATEIEN SICHERN\nAuto-Scan, Channel-Map und alle CSV-Logs anschließend gemeinsam bereitstellen."
        };

        for (String step : steps) {
            TextView v = body(step);
            v.setTextColor(TEXT);
            v.setTextSize(16);
            v.setBackgroundColor(PANEL);
            v.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(4), 0, dp(4));
            list.addView(v, lp);
        }

        sv.addView(list);
        content.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showApps() {
        content.removeAllViews();
        content.addView(title("INSTALLIERTE FAHRZEUG-APPS"));

        TextView help = body("Die Liste wird direkt aus den auf diesem Radio installierten, startbaren Apps erzeugt. Es sind keine erfundenen Verknüpfungen hinterlegt.");
        help.setPadding(0, dp(6), 0, dp(12));
        content.addView(help);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        List<Launchable> apps = allLaunchableApps();
        for (Launchable app : apps) {
            Button b = actionButton(app.label, v -> launchPackage(app.packageName));
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            list.addView(b, new LinearLayout.LayoutParams(-1, dp(54)));
        }
        sv.addView(list);
        content.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void addQuick(GridLayout grid, String label, Runnable r) {
        Button b = actionButton(label, v -> r.run());
        GridLayout.LayoutParams lp = tileParams();
        lp.height = dp(90);
        grid.addView(b, lp);
    }

    private void openPhone() {
        Intent dial = new Intent(Intent.ACTION_DIAL);
        if (dial.resolveActivity(getPackageManager()) != null) {
            startActivity(dial);
            return;
        }
        openMatchedApp(new String[]{"phone","dialer","bluetooth","bt phone","telefon"}, "Telefon");
    }

    private void openNavigation() {
        Intent nav = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q="));
        if (nav.resolveActivity(getPackageManager()) != null) {
            startActivity(nav);
            return;
        }
        openMatchedApp(new String[]{"maps","waze","navigation","navi"}, "Navigation");
    }

    private void openSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            toast("Android-Einstellungen konnten nicht geöffnet werden.");
        }
    }

    private void openMatchedApp(String[] tokens, String category) {
        List<Launchable> matches = findApps(tokens);
        if (matches.isEmpty()) {
            toast(category + ": keine passende installierte App gefunden.");
            return;
        }
        if (matches.size() == 1) {
            launchPackage(matches.get(0).packageName);
            return;
        }
        String[] names = new String[matches.size()];
        for (int i=0;i<matches.size();i++) names[i] = matches.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(category + " auswählen")
                .setItems(names, (d, which) -> launchPackage(matches.get(which).packageName))
                .show();
    }

    private List<Launchable> findApps(String[] tokens) {
        List<Launchable> out = new ArrayList<>();
        for (Launchable a : allLaunchableApps()) {
            String hay = (a.label + " " + a.packageName).toLowerCase(Locale.ROOT);
            for (String token : tokens) {
                if (hay.contains(token.toLowerCase(Locale.ROOT))) {
                    out.add(a);
                    break;
                }
            }
        }
        return out;
    }

    private List<Launchable> allLaunchableApps() {
        List<Launchable> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo ai : apps) {
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            CharSequence label = pm.getApplicationLabel(ai);
            out.add(new Launchable(label == null ? ai.packageName : label.toString(), ai.packageName));
        }
        Collections.sort(out, Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
        return out;
    }

    private void launchPackage(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            toast("App ist nicht startbar: " + pkg);
            return;
        }
        try { startActivity(i); }
        catch (Exception e) { toast("Start fehlgeschlagen: " + e.getMessage()); }
    }

    private void requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQ_BT);
        }
    }

    private void connect() {
        if (polling) {
            toast("OBD ist bereits verbunden.");
            return;
        }
        setConnectionState("OBD: verbinde …", PANEL_2);
        io.execute(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) throw new Exception("Dieses Gerät meldet keinen Bluetooth-Adapter.");
                if (!adapter.isEnabled()) throw new Exception("Bluetooth ist ausgeschaltet.");

                BluetoothDevice target = null;
                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                for (BluetoothDevice d : bonded) {
                    String name = d.getName();
                    if (name == null) continue;
                    String u = name.toUpperCase(Locale.ROOT);
                    if (u.equals("OBDII") || u.contains("OBD") || u.contains("ELM327")) {
                        target = d;
                        break;
                    }
                }
                if (target == null) throw new Exception("Kein bereits gekoppelter OBD/ELM327-Adapter gefunden.");

                postStatus("Verbinde mit " + target.getName() + " …");
                socket = target.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                writer = socket.getOutputStream();
                reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));

                initElm();
                discoverSupportedPids();
                polling = true;
                appendSystemLog("CONNECTED", target.getName());
                setConnectionState("OBD: verbunden", OK);
                postStatus("ECU verbunden. Angezeigt werden nur bestätigte Live-Werte.");
                pollLoop();
            } catch (Exception e) {
                appendSystemLog("CONNECT_ERROR", safe(e.getMessage()));
                setConnectionState("OBD: getrennt", RED);
                postStatus("Verbindung fehlgeschlagen: " + safe(e.getMessage()));
                closeSocket();
            }
        });
    }

    private void initElm() throws Exception {
        logCommand("ATZ", send("ATZ"));
        logCommand("ATE0", send("ATE0"));
        logCommand("ATL0", send("ATL0"));
        logCommand("ATS0", send("ATS0"));
        logCommand("ATH0", send("ATH0"));
        logCommand("ATSP0", send("ATSP0"));
        String dp = send("ATDP");
        protocolName = dp == null || dp.trim().isEmpty() ? "unbekannt" : dp.trim();
        logCommand("ATDP", dp);
        logCommand("ATDPN", send("ATDPN"));
    }

    private void discoverSupportedPids() throws Exception {
        support.clear();
        String[] bases = {"0100","0120","0140","0160"};
        for (String cmd : bases) {
            String raw = send(cmd);
            appendRawDiscovery(cmd, raw);
            markSupportBlock(cmd, raw);
        }
    }

    private void markSupportBlock(String cmd, String raw) {
        String s = raw == null ? "" : raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        String key = "41" + cmd.substring(2);
        int at = s.indexOf(key);
        if (at < 0 || s.length() < at + 12) return;
        String hex = s.substring(at + 4, at + 12);
        long bits;
        try { bits = Long.parseLong(hex, 16); } catch (Exception e) { return; }

        int base = Integer.parseInt(cmd.substring(2), 16);
        for (int n=1;n<=32;n++) {
            boolean yes = (bits & (1L << (32-n))) != 0;
            support.put(String.format(Locale.ROOT, "01%02X", base+n), yes);
        }
    }

    private void pollLoop() {
        while (polling && socket != null && socket.isConnected()) {
            for (Pid p : pids) {
                if (!polling) break;
                if (!Boolean.TRUE.equals(support.get(p.cmd))) continue;
                try {
                    String raw = send(p.cmd);
                    Double value = decode(p.cmd, raw);
                    String state = classify(raw, value);
                    appendPidLog(p, raw, value, state);
                    if (value != null) updateTile(p.label, fmt(value), p.unit);
                } catch (Exception e) {
                    appendPidLog(p, "", null, "ERROR:" + safe(e.getMessage()));
                }
            }
        }
    }

    private String send(String cmd) throws Exception {
        writer.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        writer.flush();
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + 2200;
        while (System.currentTimeMillis() < end) {
            if (reader.ready()) {
                int c = reader.read();
                if (c < 0) break;
                char ch = (char)c;
                if (ch == '>') break;
                sb.append(ch);
            } else {
                Thread.sleep(18);
            }
        }
        return sb.toString().replace("\r", " ").replace("\n", " ").trim();
    }

    private String classify(String raw, Double value) {
        String u = raw == null ? "" : raw.toUpperCase(Locale.ROOT);
        if (value != null) return "OK";
        if (u.contains("NO DATA")) return "NO_DATA";
        if (u.contains("CAN ERROR")) return "CAN_ERROR";
        if (u.contains("BUS ERROR")) return "BUS_ERROR";
        if (u.contains("STOPPED")) return "STOPPED";
        if (u.contains("UNABLE TO CONNECT")) return "UNABLE_TO_CONNECT";
        if (u.matches(".*7F[0-9A-F]{2}[0-9A-F]{2}.*")) return "NEGATIVE_RESPONSE";
        if (u.trim().isEmpty()) return "EMPTY";
        return "UNPARSED";
    }

    private Double decode(String cmd, String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        String key = "41" + cmd.substring(2);
        int i = s.indexOf(key);
        if (i < 0) return null;
        String d = s.substring(i + 4);
        try {
            int A = d.length() >= 2 ? Integer.parseInt(d.substring(0,2),16) : 0;
            int B = d.length() >= 4 ? Integer.parseInt(d.substring(2,4),16) : 0;
            switch (cmd) {
                case "010C": return ((A * 256) + B) / 4.0;
                case "010D": return (double)A;
                case "0105":
                case "010F":
                case "0146":
                case "015C": return (double)A - 40.0;
                case "010B":
                case "0133": return (double)A;
                case "0110": return ((A * 256) + B) / 100.0;
                case "0111":
                case "0104":
                case "012F":
                case "0149":
                case "014C": return A * 100.0 / 255.0;
                case "010E": return A / 2.0 - 64.0;
                case "0142": return ((A * 256) + B) / 1000.0;
                case "015E": return ((A * 256) + B) / 20.0;
                case "0162": return (double)A - 125.0;
                case "0163": return (double)((A * 256) + B);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void updateTile(String label, String value, String unit) {
        ui.post(() -> {
            if (liveGrid == null) return;
            TextView found = null;
            for (int i=0;i<liveGrid.getChildCount();i++) {
                View v = liveGrid.getChildAt(i);
                if (v instanceof TextView && label.equals(v.getTag())) {
                    found = (TextView)v;
                    break;
                }
            }
            double numeric;
            try { numeric = Double.parseDouble(value.replace(',', '.')); } catch (Exception ex) { numeric = Double.NaN; }
            if (!Double.isNaN(numeric)) {
                Double min = minSeen.get(label);
                Double max = maxSeen.get(label);
                if (min == null || numeric < min) minSeen.put(label, numeric);
                if (max == null || numeric > max) maxSeen.put(label, numeric);
            }
            String range = "";
            if (minSeen.containsKey(label) && maxSeen.containsKey(label)) {
                range = "\nmin " + fmt(minSeen.get(label)) + "  max " + fmt(maxSeen.get(label));
            }
            String txt = label + "\n" + value + " " + unit + range;
            if (found != null) {
                found.setText(txt);
            } else {
                if (liveGrid.getChildCount() == 1) {
                    View first = liveGrid.getChildAt(0);
                    if (first instanceof TextView && ((TextView)first).getTag() == null) liveGrid.removeAllViews();
                }
                TextView tile = new TextView(this);
                tile.setTag(label);
                tile.setText(txt);
                tile.setTextColor(TEXT);
                tile.setTextSize(21);
                tile.setGravity(Gravity.CENTER);
                tile.setPadding(dp(8), dp(18), dp(8), dp(18));
                tile.setBackgroundColor(PANEL);
                liveGrid.addView(tile, tileParams());
            }
        });
    }

    private void resetLog() {
        synchronized (logLock) {
            csvLog.setLength(0);
            minSeen.clear();
            maxSeen.clear();
            csvLog.append("timestamp;elapsed_ms;pid;label;raw_response;decoded_value;unit;status\n");
            logRows = 0;
            logStartedAt = System.currentTimeMillis();
        }
        updateLogBadge();
    }

    private void appendPidLog(Pid p, String raw, Double value, String state) {
        synchronized (logLock) {
            csvLog.append(now()).append(';')
                    .append(System.currentTimeMillis() - logStartedAt).append(';')
                    .append(csv(p.cmd)).append(';')
                    .append(csv(p.label)).append(';')
                    .append(csv(raw)).append(';')
                    .append(value == null ? "" : csv(fmt(value))).append(';')
                    .append(csv(p.unit)).append(';')
                    .append(csv(state)).append('\n');
            logRows++;
        }
        updateLogBadge();
    }

    private void appendRawDiscovery(String cmd, String raw) {
        synchronized (logLock) {
            csvLog.append(now()).append(';')
                    .append(System.currentTimeMillis() - logStartedAt).append(';')
                    .append(csv(cmd)).append(';')
                    .append("PID_SUPPORT;")
                    .append(csv(raw)).append(";;;DISCOVERY\n");
            logRows++;
        }
        updateLogBadge();
    }

    private void appendSystemLog(String state, String detail) {
        synchronized (logLock) {
            csvLog.append(now()).append(';')
                    .append(System.currentTimeMillis() - logStartedAt).append(';')
                    .append("SYSTEM;;;")
                    .append(csv(detail)).append(";;;")
                    .append(csv(state)).append('\n');
            logRows++;
        }
        updateLogBadge();
    }

    private void logCommand(String cmd, String raw) {
        synchronized (logLock) {
            csvLog.append(now()).append(';')
                    .append(System.currentTimeMillis() - logStartedAt).append(';')
                    .append(csv(cmd)).append(';')
                    .append("ELM_INIT;")
                    .append(csv(raw)).append(";;;INIT\n");
            logRows++;
        }
        updateLogBadge();
    }

    private String newLogFileName() {
        return "135er_Touran_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(new Date()) + ".csv";
    }

    private void exportLog() {
        io.execute(() -> {
            if (tryExportToUsb()) return;
            ui.post(() -> {
                toast("Kein direkt beschreibbarer USB-Speicher erkannt. Öffne Dateiauswahl …");
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("text/csv");
                i.putExtra(Intent.EXTRA_TITLE, newLogFileName());
                startActivityForResult(i, REQ_EXPORT_LOG);
            });
        });
    }

    private boolean tryExportToUsb() {
        File[] dirs = getExternalFilesDirs(null);
        if (dirs == null) return false;

        String snapshot;
        synchronized (logLock) { snapshot = csvLog.toString(); }

        for (File dir : dirs) {
            if (dir == null) continue;
            boolean removable;
            try {
                removable = Environment.isExternalStorageRemovable(dir);
            } catch (Exception e) {
                removable = false;
            }
            if (!removable) continue;

            try {
                File folder = new File(dir, "135erTouranLogs");
                if (!folder.exists() && !folder.mkdirs()) continue;
                File outFile = new File(folder, newLogFileName());
                try (FileOutputStream out = new FileOutputStream(outFile, false)) {
                    out.write(snapshot.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                appendSystemLog("USB_EXPORT", outFile.getAbsolutePath());
                toast("Log auf USB gespeichert:\n" + outFile.getAbsolutePath());
                return true;
            } catch (Exception e) {
                appendSystemLog("USB_EXPORT_ERROR", safe(e.getMessage()));
            }
        }
        return false;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT_LOG && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            io.execute(() -> {
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    String snapshot;
                    synchronized (logLock) { snapshot = csvLog.toString(); }
                    out.write(snapshot.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    toast("Log gespeichert.");
                } catch (Exception e) {
                    toast("Speichern fehlgeschlagen: " + safe(e.getMessage()));
                }
            });
        }
    }

    private void disconnect() {
        polling = false;
        io.execute(() -> {
            appendSystemLog("DISCONNECTED", "manual");
            closeSocket();
            setConnectionState("OBD: getrennt", RED);
            postStatus("OBD getrennt. Der Log bleibt zum Export erhalten.");
        });
    }

    private void closeSocket() {
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        socket = null;
        reader = null;
        writer = null;
    }

    private void setConnectionState(String text, int color) {
        ui.post(() -> {
            if (connectionBadge != null) {
                connectionBadge.setText(text);
                connectionBadge.setBackgroundColor(color);
            }
        });
    }

    private void updateLogBadge() {
        ui.post(() -> {
            if (loggerBadge != null) loggerBadge.setText("LOG: " + logRows);
        });
    }

    private void postStatus(String s) {
        ui.post(() -> {
            if (statusLine != null) statusLine.setText(s);
        });
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.GERMANY).format(new Date());
    }

    private String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"","\"\"") + "\"";
    }

    private String safe(String s) { return s == null ? "" : s; }

    private String fmt(double v) {
        if (Math.abs(v) >= 100 || Math.rint(v) == v)
            return String.format(Locale.GERMANY, "%.0f", v);
        return String.format(Locale.GERMANY, "%.1f", v);
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setTextSize(28);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(MUTED);
        t.setTextSize(15);
        return t;
    }

    private TextView badge(String s, int bg) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(TEXT);
        t.setBackgroundColor(bg);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(6), 0, 0, 0);
        t.setLayoutParams(lp);
        return t;
    }

    private Button navButton(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(13);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(l);
        return b;
    }

    private Button actionButton(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TEXT);
        b.setTextSize(15);
        b.setBackgroundColor(PANEL_2);
        b.setOnClickListener(l);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(dp(4), dp(3), dp(4), dp(3));
        return lp;
    }

    private GridLayout.LayoutParams tileParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        lp.setMargins(dp(5), dp(5), dp(5), dp(5));
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        ui.post(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }

    @Override protected void onDestroy() {
        polling = false;
        closeSocket();
        io.shutdownNow();
        super.onDestroy();
    }
}
