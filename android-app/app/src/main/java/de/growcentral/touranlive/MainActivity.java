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
import android.graphics.Canvas;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
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
import java.io.InputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.HashMap;
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
    private static final String LOG_UPLOAD_URL = "https://www.dezender.de/touran/api/upload-log.php";

    private static final int BG = Color.rgb(5, 7, 10);
    private static final int PANEL = Color.rgb(17, 20, 24);
    private static final int PANEL_2 = Color.rgb(27, 31, 36);
    private static final int RED = Color.rgb(205, 28, 38);
    private static final int TEXT = Color.rgb(242, 242, 242);
    private static final int MUTED = Color.rgb(155, 160, 166);
    private static final int OK = Color.rgb(75, 190, 120);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    // Network jobs must not share one endless queue: rolling upload, system audit and self-check run independently.
    private final ExecutorService netIo = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());

    private BluetoothSocket socket;
    private BufferedReader reader;
    private OutputStream writer;
    private volatile boolean polling = false;
    private volatile boolean autoUploadRunning = false;
    private long lastAutoUploadAt = 0L;
    private static final long AUTO_UPLOAD_INTERVAL_MS = 60000L;
    private volatile boolean vagMode = false;
    private VagTp20 vag;

    private LinearLayout content;
    private TextView connectionBadge;
    private TextView loggerBadge;
    private TextView statusLine;
    private GridLayout liveGrid;
    private DashboardView dashboardView;
    private final Map<String, Double> liveValues = new LinkedHashMap<>();
    private final List<String> storedDtcs = new ArrayList<>();
    private final List<String> pendingDtcs = new ArrayList<>();
    private final List<String> permanentDtcs = new ArrayList<>();
    private String dtcStatus = "nicht geprüft";

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

    // Exakt aus VCDS blockmap-OBD vom 05.10.2026: nur vom CAVC/MED17.5.5 bestaetigte Mode-01-PIDs.
    private final Pid[] pids = new Pid[] {
        new Pid("0104","Motorlast","%"),
        new Pid("0105","Kühlmittel","°C"),
        new Pid("0106","Fuel Trim kurz","%"),
        new Pid("0107","Fuel Trim lang","%"),
        new Pid("010B","Saugrohrdruck","kPa"),
        new Pid("010C","Drehzahl","rpm"),
        new Pid("010D","Geschwindigkeit","km/h"),
        new Pid("010E","Zündwinkel","°"),
        new Pid("010F","Ansaugluft","°C"),
        new Pid("0111","Drosselklappe","%"),
        new Pid("011F","Motorlaufzeit","s"),
        new Pid("0121","Strecke MIL","km"),
        new Pid("0123","Kraftstoffdruck","bar"),
        new Pid("012E","Tankentlüftung","%"),
        new Pid("0130","Warmlaufzyklen",""),
        new Pid("0131","Strecke seit Fehlerlöschung","km"),
        new Pid("0133","Umgebungsdruck","kPa"),
        new Pid("0134","Lambda Ist","λ"),
        new Pid("013C","Kat-Temperatur","°C"),
        new Pid("0142","ECU-Spannung","V"),
        new Pid("0143","Absolute Last","%"),
        new Pid("0144","Lambda Soll","λ"),
        new Pid("0145","Drossel relativ","%"),
        new Pid("0146","Außentemperatur","°C"),
        new Pid("0147","Drossel B","%"),
        new Pid("0149","Pedalstellung","%"),
        new Pid("014A","Pedalstellung E","%"),
        new Pid("014C","Drossel Soll","%"),
        new Pid("0156","Lambda Trim lang B1","%")
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        resetLog();
        buildShell();
        showVehicle();
        requestBtPermission();
        startAutoUploadLoop();
        netIo.execute(this::autoSendRadioAuditOnce);
        netIo.execute(() -> performSelfCheck(false));
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
        nav.addView(navButton("TACHO", v -> showVehicle()), weight());
        nav.addView(navButton("LIVE", v -> showHome()), weight());
        nav.addView(navButton("LOGGER", v -> showLogger()), weight());
        nav.addView(navButton("DIAGNOSE", v -> showDiagnostics()), weight());
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
        car.setImageResource(de.growcentral.touranlive.R.drawable.touran_master_final);
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
        liveGrid = null;
        dashboardView = new DashboardView();
        setContentView(dashboardView);
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
        row.addView(actionButton("LOG AN SERVER", v -> uploadCurrentLog(false)), weight());
        row.addView(actionButton("ADAPTER SCAN + SEND", v -> runCapabilityScanAndUpload()), weight());
        row.addView(actionButton("LOG LEEREN", v -> {
            resetLog();
            showLogger();
            toast("Logger geleert.");
        }), weight());
        content.addView(row);
        LinearLayout checkRow = new LinearLayout(this);
        checkRow.setPadding(0, dp(10), 0, 0);
        checkRow.addView(actionButton("SYSTEM-/RECHTECHECK", v -> netIo.execute(() -> performSelfCheck(true))), weight());
        content.addView(checkRow);
    }

    private void showDiagnostics() {
        content.removeAllViews();
        dashboardView = null;

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = title("MOTOR-DIAGNOSE • CAVC");
        head.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(actionButton("FEHLER LESEN", v -> readDiagnostics()), weight());
        head.addView(actionButton("FAHRZEUG / PR", v -> showVehicleProfile()), weight());
        content.addView(head);

        TextView info = body("Liest gespeicherte, schwebende und permanente OBD-Fehler aus dem Motorsteuergerät. Es werden keine Fehler gelöscht und keine Steuergerätewerte verändert.");
        info.setPadding(0, dp(6), 0, dp(12));
        content.addView(info);

        TextView state = body("Status: " + dtcStatus);
        state.setTextColor(storedDtcs.isEmpty() && pendingDtcs.isEmpty() && permanentDtcs.isEmpty() && !"nicht geprüft".equals(dtcStatus) ? OK : TEXT);
        state.setTextSize(20);
        state.setPadding(0, 0, 0, dp(10));
        content.addView(state);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        addDtcSection(list, "GESPEICHERT • Mode 03", storedDtcs);
        addDtcSection(list, "SCHWEBEND • Mode 07", pendingDtcs);
        addDtcSection(list, "PERMANENT • Mode 0A", permanentDtcs);

        sv.addView(list);
        content.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void showVehicleProfile() {
        content.removeAllViews();
        content.addView(title("FAHRZEUGPROFIL • PR-AUSSTATTUNG"));
        TextView base = body(VehicleProfile.MODEL + " • Modelljahr " + VehicleProfile.MODEL_YEAR + " • Produktion " + VehicleProfile.PRODUCTION_DATE +
                "\n" + VehicleProfile.ENGINE_TEXT + " • Getriebe " + VehicleProfile.GEARBOX + " • " + VehicleProfile.DRIVE +
                "\nVerkaufstyp " + VehicleProfile.SALES_TYPE + " • Lack " + VehicleProfile.COLOR + " • " + VehicleProfile.TRIM +
                "\nBatterie " + VehicleProfile.BATTERY + " • Generator " + VehicleProfile.GENERATOR +
                "\nAb Werk: " + VehicleProfile.FACTORY_RADIO + " • " + VehicleProfile.TYRES);
        base.setPadding(0, dp(6), 0, dp(12));
        content.addView(base);
        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView hp = body("VORHANDEN / APP-RELEVANT"); hp.setTextColor(OK); hp.setTypeface(Typeface.DEFAULT_BOLD); list.addView(hp);
        for (String x : VehicleProfile.PRESENT_PR) { TextView v=body("✓  " + x); v.setPadding(dp(8),dp(4),0,dp(4)); list.addView(v); }
        TextView ha = body("\nAB WERK NICHT VORHANDEN"); ha.setTextColor(MUTED); ha.setTypeface(Typeface.DEFAULT_BOLD); list.addView(ha);
        for (String x : VehicleProfile.ABSENT_PR) { TextView v=body("—  " + x); v.setTextColor(MUTED); v.setPadding(dp(8),dp(4),0,dp(4)); list.addView(v); }
        TextView note = body("\nDie PR-Liste beschreibt die Werksausstattung. Live-Werte werden nur angezeigt, wenn VCDS/VAG oder ein bestätigter OBD-PID tatsächlich Daten liefert.");
        note.setTextColor(MUTED); note.setPadding(0,dp(8),0,dp(16)); list.addView(note);
        sv.addView(list); content.addView(sv, new LinearLayout.LayoutParams(-1,0,1));
    }

    private void addDtcSection(LinearLayout list, String title, List<String> items) {
        TextView h = body(title);
        h.setTextColor(RED);
        h.setTextSize(17);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setPadding(0, dp(8), 0, dp(5));
        list.addView(h);

        if (items.isEmpty()) {
            TextView none = body("Keine gemeldeten Fehler.");
            none.setTextColor(MUTED);
            none.setPadding(dp(12), dp(8), dp(12), dp(12));
            list.addView(none);
            return;
        }

        for (String item : items) {
            TextView row = body(item);
            row.setTextColor(TEXT);
            row.setTextSize(17);
            row.setBackgroundColor(PANEL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(3), 0, dp(3));
            list.addView(row, lp);
        }
    }

    private void readDiagnostics() {
        if (socket == null || !socket.isConnected()) {
            toast("Zuerst OBD verbinden.");
            return;
        }

        dtcStatus = "wird gelesen …";
        showDiagnostics();

        polling = false;
        io.execute(() -> {
            try {
                Thread.sleep(120);
                String rawStored = send("03");
                String rawPending = send("07");
                String rawPermanent = send("0A");

                List<String> a = parseDtcResponse(rawStored, "43");
                List<String> b = parseDtcResponse(rawPending, "47");
                List<String> c = parseDtcResponse(rawPermanent, "4A");

                synchronized (storedDtcs) {
                    storedDtcs.clear();
                    storedDtcs.addAll(a);
                    pendingDtcs.clear();
                    pendingDtcs.addAll(b);
                    permanentDtcs.clear();
                    permanentDtcs.addAll(c);
                }

                appendDiagnosticLog("03", rawStored, a);
                appendDiagnosticLog("07", rawPending, b);
                appendDiagnosticLog("0A", rawPermanent, c);

                int total = a.size() + b.size() + c.size();
                dtcStatus = total == 0 ? "Keine Fehler gemeldet" : total + " Fehler/Statusmeldungen gefunden";
            } catch (Exception e) {
                dtcStatus = "Fehler beim Auslesen: " + safe(e.getMessage());
                appendSystemLog("DTC_READ_ERROR", safe(e.getMessage()));
            }

            ui.post(() -> {
                showDiagnostics();
                if (dashboardView != null) dashboardView.invalidate();
            });

            if (socket != null && socket.isConnected()) {
                polling = true;
                pollLoop();
            }
        });
    }

    private void appendDiagnosticLog(String mode, String raw, List<String> dtcs) {
        synchronized (logLock) {
            csvLog.append(now()).append(';')
                    .append(System.currentTimeMillis() - logStartedAt).append(';')
                    .append(csv(mode)).append(';')
                    .append("DTC_READ;")
                    .append(csv(raw)).append(';')
                    .append(csv(String.join(" | ", dtcs))).append(";;DIAGNOSIS\n");
            logRows++;
        }
        updateLogBadge();
    }

    private List<String> parseDtcResponse(String raw, String serviceResponse) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("NO DATA") || upper.contains("UNABLE TO CONNECT")) return out;

        String hex = raw.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        int pos = hex.indexOf(serviceResponse);
        if (pos < 0) return out;
        String payload = hex.substring(pos + 2);

        for (int i = 0; i + 4 <= payload.length(); i += 4) {
            String word = payload.substring(i, i + 4);
            if ("0000".equals(word)) continue;
            try {
                int b1 = Integer.parseInt(word.substring(0, 2), 16);
                int b2 = Integer.parseInt(word.substring(2, 4), 16);
                String code = dtcCode(b1, b2);
                if (!code.matches("[PCBU][0-3][0-9A-F]{3}")) continue;
                String desc = dtcDescription(code);
                String text = code + (desc.isEmpty() ? "" : " • " + desc);
                if (!out.contains(text)) out.add(text);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private String dtcCode(int a, int b) {
        char family = "PCBU".charAt((a >> 6) & 0x03);
        int d1 = (a >> 4) & 0x03;
        int d2 = a & 0x0F;
        int d3 = (b >> 4) & 0x0F;
        int d4 = b & 0x0F;
        return String.format(Locale.ROOT, "%c%d%X%X%X", family, d1, d2, d3, d4);
    }

    private String dtcDescription(String code) {
        switch (code) {
            case "P0016": return "Kurbelwelle/Nockenwelle Bank 1 – Zuordnung unplausibel";
            case "P0230": return "Kraftstoffpumpe – Primärkreis Fehlfunktion";
            case "P0234": return "Ladedruckregelung – Grenzwert überschritten";
            case "P0299": return "Ladedruckregelung – Regelgrenze unterschritten";
            case "P0300": return "Zufällige/mehrfache Verbrennungsaussetzer";
            case "P0301": return "Verbrennungsaussetzer Zylinder 1";
            case "P0302": return "Verbrennungsaussetzer Zylinder 2";
            case "P0303": return "Verbrennungsaussetzer Zylinder 3";
            case "P0304": return "Verbrennungsaussetzer Zylinder 4";
            case "P0363": return "Fehlzündung erkannt – Kraftstoffzufuhr abgeschaltet";
            case "P1550": return "Ladedruckregelung / N75 – Regelabweichung";
            case "P1555": return "Ladedruckregelung – obere Regelgrenze überschritten";
            case "P2181": return "Kühlsystem – Funktion außerhalb Sollbereich";
            case "P2187": return "Gemisch im Leerlauf zu mager";
            case "P2279": return "Leck im Ansaugsystem";
            case "P2293": return "Kraftstoffdruckregelung / N276 – mechanische Fehlfunktion";
            default: return "";
        }
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

    private boolean hasInternet() {
        try {
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if (cm==null) return false;
            android.net.Network n=cm.getActiveNetwork();
            if (n==null) return false;
            NetworkCapabilities c=cm.getNetworkCapabilities(n);
            return c!=null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch(Exception e) { return false; }
    }

    private void performSelfCheck(boolean showDialog) {
        StringBuilder r = new StringBuilder();
        boolean internetPerm = getPackageManager().checkPermission(Manifest.permission.INTERNET, getPackageName()) == PackageManager.PERMISSION_GRANTED;
        boolean btConnect = Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        boolean btScan = Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        boolean btPresent = a != null;
        boolean btEnabled = false;
        boolean pairedObd = false;
        try {
            btEnabled = a != null && a.isEnabled();
            if (a != null && btConnect) {
                for (BluetoothDevice d : a.getBondedDevices()) {
                    String n = d.getName();
                    if (n != null) {
                        String u = n.toUpperCase(Locale.ROOT);
                        if (u.contains("OBD") || u.contains("ELM327")) { pairedObd = true; break; }
                    }
                }
            }
        } catch (Exception ignored) {}
        boolean net = hasInternet();
        boolean storage = getFilesDir() != null && getFilesDir().canWrite();
        String server = net ? testUploadEndpoint() : "kein Internet";
        r.append(internetPerm ? "OK" : "FEHLER").append("  INTERNET-Recht\n");
        r.append(btPresent ? "OK" : "FEHLER").append("  Bluetooth-Hardware\n");
        r.append(btEnabled ? "OK" : "FEHLER").append("  Bluetooth eingeschaltet\n");
        r.append(btConnect ? "OK" : "FEHLER").append("  BLUETOOTH_CONNECT\n");
        r.append(btScan ? "OK" : "FEHLER").append("  BLUETOOTH_SCAN\n");
        r.append(pairedObd ? "OK" : "WARNUNG").append("  gekoppelter OBD/ELM327 gefunden\n");
        r.append(net ? "OK" : "FEHLER").append("  Internet validiert\n");
        r.append(storage ? "OK" : "FEHLER").append("  App-Speicher beschreibbar\n");
        r.append("INFO  Fahrzeugprofil: ").append(VehicleProfile.MODEL).append(" / ").append(VehicleProfile.ENGINE).append(" / ").append(VehicleProfile.GEARBOX).append("\n");
        r.append("INFO  9Q5 MFA+, 7X5 Parklenkassistent, 9AK Climatronic, 8T2 GRA vorhanden\n");
        r.append("INFO  7K0 kein RDK, 7L3 kein Start/Stopp, 7Q0 kein Werks-Navi, 9W0 kein Werks-Telefon\n");
        r.append("SERVER  ").append(server).append('\n');
        r.append("Speicher-Hinweis: Für App-internen Speicher und Android-Dateiauswahl ist kein altes WRITE_EXTERNAL_STORAGE-Recht nötig.");
        appendSystemLog("SELF_CHECK", r.toString().replace('\n','|'));
        if (showDialog) ui.post(() -> new AlertDialog.Builder(this).setTitle("135er Touran System-/Rechtecheck").setMessage(r.toString()).setPositiveButton("OK", null).show());
    }

    private String testUploadEndpoint() {
        HttpURLConnection c = null;
        try {
            URL u = new URL(LOG_UPLOAD_URL);
            c = (HttpURLConnection)u.openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(7000); c.setReadTimeout(9000);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            c.setRequestProperty("X-Touran-Install", getInstallId());
            c.setRequestProperty("X-Touran-Report", "log");
            byte[] b = ("TouranLive selfcheck 0.5.7 " + now()).getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(b.length);
            try (OutputStream o = c.getOutputStream()) { o.write(b); }
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String body = "";
            if (in != null) {
                byte[] buf = new byte[1024]; int n; StringBuilder s = new StringBuilder();
                while ((n=in.read(buf)) > 0 && s.length() < 3000) s.append(new String(buf,0,n,StandardCharsets.UTF_8));
                in.close(); body=s.toString().trim();
            }
            return "HTTP " + code + (body.isEmpty()?"":"  " + body);
        } catch (Exception e) {
            return "FEHLER " + safe(e.getMessage());
        } finally { if (c != null) c.disconnect(); }
    }

    private void startAutoUploadLoop() {
        if (autoUploadRunning) return;
        autoUploadRunning=true;
        netIo.execute(() -> {
            while (autoUploadRunning) {
                try {
                    long now=System.currentTimeMillis();
                    if (hasInternet() && now-lastAutoUploadAt>=AUTO_UPLOAD_INTERVAL_MS) {
                        uploadCurrentLog(true);
                        lastAutoUploadAt=now;
                    }
                    Thread.sleep(5000);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                catch (Exception ignored) {}
            }
        });
    }

    private void autoSendRadioAuditOnce() {
        try {
            if (!hasInternet()) return;
            Thread.sleep(2500);
            runRadioSystemScanAndSend();
        } catch (Exception ignored) {}
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

                // First collect the exact Mode-01 values confirmed by yesterday's VCDS OBD blockmap.
                // VAG then overrides matching fields; OBD remains the per-field fallback, never a dummy value.
                try {
                    initElm();
                    discoverSupportedPids();
                    captureConfirmedObdBaseline();
                } catch (Exception baselineError) {
                    appendSystemLog("OBD_BASELINE_ERROR", safe(baselineError.getMessage()));
                }

                VagTp20 candidate = new VagTp20(reader, writer, this::appendSystemLog);
                if (candidate.openEngine()) {
                    vag = candidate;
                    vagMode = true;
                    polling = true;
                    protocolName = "VAG TP2.0 / KWP2000";
                    appendSystemLog("CONNECTED_VAG", target.getName());
                    setConnectionState("VAG: verbunden", OK);
                    postStatus("CAVC via VAG TP2.0/KWP2000 verbunden. VCDS-Messwertbloecke werden live gelesen.");
                    netIo.execute(() -> uploadCurrentLogInternal(true));
                    pollVagLoop();
                    return;
                }

                vag = null;
                vagMode = false;
                appendSystemLog("VAG_FALLBACK", "TP2.0/KWP2000 nicht verfuegbar; Standard-OBD wird verwendet");
                initElm();
                discoverSupportedPids();
                polling = true;
                appendSystemLog("CONNECTED_OBD", target.getName());
                setConnectionState("OBD: verbunden", OK);
                netIo.execute(() -> uploadCurrentLogInternal(true));
                postStatus("Standard-OBD aktiv. VAG-Direktzugriff war mit diesem Adapter nicht verfuegbar.");
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
        String sp6 = send("ATSP6");
        logCommand("ATSP6", sp6);
        if (sp6 == null || sp6.toUpperCase(Locale.ROOT).contains("ERROR") || sp6.contains("?")) logCommand("ATSP0", send("ATSP0"));
        logCommand("ATAT2", send("ATAT2"));
        logCommand("ATST08", send("ATST08")); // 32 ms base timeout; app still enforces bounded read windows
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

    private void captureConfirmedObdBaseline() {
        String[] baseline = {"010C","010D","0105","010F","0146","0142","0133","010B","0104","0111","0149","0123","0134","0144","0106","0107","013C","010E"};
        for (String cmd : baseline) {
            Pid target = null;
            for (Pid p : pids) if (p.cmd.equals(cmd)) { target = p; break; }
            if (target == null) continue;
            try {
                String raw = sendLive(cmd);
                Double value = decode(cmd, raw);
                appendPidLog(target, raw, value, "BASELINE_" + classify(raw, value));
                if (value != null) updateTile(target.label, fmt(value), target.unit);
            } catch (Exception e) {
                appendPidLog(target, "", null, "BASELINE_ERROR:" + safe(e.getMessage()));
            }
        }
    }

    private void probeUnadvertisedPid(String cmd) {
        Pid target = null;
        for (Pid p : pids) if (p.cmd.equals(cmd)) { target = p; break; }
        if (target == null) return;
        try {
            String raw = send(cmd);
            Double value = decode(cmd, raw);
            appendPidLog(target, raw, value, "PROBE_" + classify(raw, value));
            if (value != null) {
                support.put(cmd, true);
                updateTile(target.label, fmt(value), target.unit);
            }
        } catch (Exception e) {
            appendPidLog(target, "", null, "PROBE_ERROR:" + safe(e.getMessage()));
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

    private final Map<Integer, Integer> vagGroupFailures = new HashMap<>();
    private final Map<Integer, Long> vagGroupBackoffUntil = new HashMap<>();

    private VagTp20.Block readVagGroupSafe(int group, boolean core) throws Exception {
        long now = SystemClock.elapsedRealtime();
        Long until = vagGroupBackoffUntil.get(group);
        if (!core && until != null && until > now) return null;
        try {
            VagTp20.Block b = vag.readBlock(group);
            vagGroupFailures.put(group, 0);
            vagGroupBackoffUntil.remove(group);
            return b;
        } catch (Exception e) {
            int fails = vagGroupFailures.containsKey(group) ? vagGroupFailures.get(group) + 1 : 1;
            vagGroupFailures.put(group, fails);
            appendSystemLog("VAG_GROUP_" + group + "_ERROR", safe(e.getMessage()));
            if (core && fails >= 4) throw e;
            vagGroupBackoffUntil.put(group, now + Math.min(60000L, 3000L * fails));
            return null;
        }
    }

    private void applyIfPresent(VagTp20.Block b) { if (b != null) applyVagBlock(b); }

    private void pollVagLoop() {
        // VCDS block 5 contains RPM + engine load + vehicle speed. It is the hard realtime lane.
        final int[] slowGroups = {113, 134, 210, 106, 31, 32, 20, 90, 91, 93, 15, 16};
        int slow=0, cycle=0;
        while (polling && vagMode && vag != null && vag.isOpen() && socket != null && socket.isConnected()) {
            try {
                applyIfPresent(readVagGroupSafe(5, true));
                cycle++;
                if ((cycle % 3) == 0) applyIfPresent(readVagGroupSafe(115, false));
                if ((cycle % 8) == 0) applyIfPresent(readVagGroupSafe(4, false));
                if ((cycle % 10) == 0) applyIfPresent(readVagGroupSafe(3, false));
                if ((cycle % 12) == 0) applyIfPresent(readVagGroupSafe(slowGroups[slow++ % slowGroups.length], false));
                if ((cycle % 24) == 0) vag.keepAlive();
            } catch (Exception e) {
                appendSystemLog("VAG_READ_ERROR", safe(e.getMessage()));
                try { if (vag != null) vag.close(); } catch (Exception ignored) {}
                vag = null;
                vagMode = false;
                try {
                    initElm();
                    discoverSupportedPids();
                    protocolName = "Standard OBD (VAG fallback)";
                    postStatus("VAG session lost; Standard-OBD remains active.");
                    pollLoop();
                } catch (Exception fallbackError) {
                    appendSystemLog("OBD_FALLBACK_ERROR", safe(fallbackError.getMessage()));
                    polling = false;
                }
                break;
            }
        }
    }

    private void applyVagBlock(VagTp20.Block b) {
        if (b == null) return;
        appendSystemLog("VAG_MWB_" + b.group, b.raw);
        switch (b.group) {
            case 3:
                putVag("Drehzahl", b.value(1));
                putVag("VAG_Manifold", b.value(2));
                putVag("VAG_ThrottleRel", b.value(3));
                putVag("VAG_Ignition", signedTiming(b,4));
                break;
            case 4:
                putVag("Drehzahl", b.value(1));
                putVag("VAG_Voltage", b.value(2));
                putVag("VAG_Coolant", b.value(3));
                putVag("VAG_IntakeTemp", b.value(4));
                break;
            case 5:
                putVag("Drehzahl", b.value(1));
                putVag("Motorlast", b.value(2));
                putVag("Geschwindigkeit", b.value(3));
                break;
            case 15:
                putVag("VAG_Misfire1", b.value(1)); putVag("VAG_Misfire2", b.value(2)); putVag("VAG_Misfire3", b.value(3));
                break;
            case 16:
                putVag("VAG_Misfire4", b.value(1));
                break;
            case 20:
                putVag("VAG_Knock1", signedTiming(b,1)); putVag("VAG_Knock2", signedTiming(b,2));
                putVag("VAG_Knock3", signedTiming(b,3)); putVag("VAG_Knock4", signedTiming(b,4));
                break;
            case 31:
                putVag("VAG_LambdaActual", b.value(1)); putVag("VAG_LambdaTarget", b.value(2));
                break;
            case 32:
                putVag("VAG_AdaptIdle", b.value(1)); putVag("VAG_AdaptPart", b.value(2));
                break;
            case 90:
                putVag("VAG_CamActual", b.value(3));
                break;
            case 91:
                putVag("VAG_CamTarget", b.value(3)); putVag("VAG_CamActual", b.value(4));
                break;
            case 93:
                putVag("VAG_CamAdapt", b.value(1));
                break;
            case 106:
                putVag("VAG_Rail", b.value(1));
                break;
            case 113:
                putVag("VAG_AmbientPressure", b.value(4));
                break;
            case 115:
                putVag("Drehzahl", b.value(1)); putVag("Motorlast", b.value(2));
                putVag("VAG_BoostTargetAbs", b.value(3)); putVag("VAG_BoostActualAbs", b.value(4));
                break;
            case 134:
                putVag("VAG_OilTemp", b.value(1));
                break;
            case 210:
                putVag("VAG_MAF", b.value(3));
                break;
        }
        updateDerivedVagValues();
    }

    private Double signedTiming(VagTp20.Block b, int field) {
        if (b == null || field < 1 || field > b.cells.length) return null;
        VagTp20.Cell c = b.cells[field-1];
        if (c.value == null) return null;
        return c.unit != null && c.unit.contains("n.OT") ? -c.value : c.value;
    }

    private void putVag(String key, Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) return;
        ui.post(() -> {
            liveValues.put(key, value);
            if (dashboardView != null) dashboardView.invalidate();
        });
    }

    private void updateDerivedVagValues() {
        ui.post(() -> {
            Double amb = liveValues.get("VAG_AmbientPressure");
            Double actual = liveValues.get("VAG_BoostActualAbs");
            Double target = liveValues.get("VAG_BoostTargetAbs");
            if (amb != null && actual != null) liveValues.put("VAG_BoostActualRel", (actual - amb) / 1000.0);
            if (amb != null && target != null) liveValues.put("VAG_BoostTargetRel", (target - amb) / 1000.0);
            if (dashboardView != null) dashboardView.invalidate();
        });
    }

    // ELM327 is strictly half-duplex: one request can be in flight at a time.
    // Fast gauges therefore get priority; slow/optional PIDs are sampled in small gaps only.
    private final Map<String, Long> pidBackoffUntil = new HashMap<>();
    private final Map<String, Integer> pidFailures = new HashMap<>();

    private void pollLoop() {
        final String[] fast = {"010C","010D"};
        final String[] medium = {"010B","0104","0111","0149"};
        final String[] slow = {"0105","010F","0146","0142","0133","0123","0134","0144","0106","0107","013C","0143","0145","0147","014A","014C","012E","011F","0121","0130","0131","0156"};
        int fi=0, mi=0, si=0, cycle=0;
        while (polling && socket != null && socket.isConnected()) {
            try {
                pollPidAdaptive(fast[fi++ % fast.length], true);
                cycle++;
                if ((cycle % 3) == 0) pollPidAdaptive(medium[mi++ % medium.length], true);
                if ((cycle % 12) == 0) pollPidAdaptive(slow[si++ % slow.length], false);
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void pollPidAdaptive(String cmd, boolean fastLane) {
        if (!polling) return;
        long now = SystemClock.elapsedRealtime();
        Long blocked = pidBackoffUntil.get(cmd);
        if (blocked != null && blocked > now) return;
        Pid target = null;
        for (Pid p : pids) if (p.cmd.equals(cmd)) { target = p; break; }
        if (target == null) return;
        try {
            String raw = fastLane ? sendFast(cmd) : sendLive(cmd);
            Double value = decode(cmd, raw);
            String state = classify(raw, value);
            if (value != null) {
                pidFailures.put(cmd, 0);
                pidBackoffUntil.remove(cmd);
                if (!fastLane || (++fastLogDivider % 12)==0) appendPidLog(target, raw, value, state);
                updateTile(target.label, fmt(value), target.unit);
            } else {
                int fails = pidFailures.containsKey(cmd) ? pidFailures.get(cmd) + 1 : 1;
                pidFailures.put(cmd, fails);
                appendPidLog(target, raw, null, state);
                if (fails >= 2) pidBackoffUntil.put(cmd, now + Math.min(30000L, 2500L * fails));
            }
        } catch (Exception e) {
            int fails = pidFailures.containsKey(cmd) ? pidFailures.get(cmd) + 1 : 1;
            pidFailures.put(cmd, fails);
            pidBackoffUntil.put(cmd, now + Math.min(30000L, 2500L * fails));
            appendPidLog(target, "", null, "ERROR:" + safe(e.getMessage()));
        }
    }

    private int fastLogDivider = 0;

    private String sendFast(String cmd) throws Exception {
        writer.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        writer.flush();
        return readElmResponse(125);
    }

    private String sendLive(String cmd) throws Exception {
        writer.write((cmd + "\r").getBytes(StandardCharsets.US_ASCII));
        writer.flush();
        return readElmResponse(260);
    }

    private String readElmResponse(long timeoutMs) throws Exception {
        StringBuilder sb = new StringBuilder();
        long end = SystemClock.elapsedRealtime() + timeoutMs;
        boolean gotAny = false;
        while (SystemClock.elapsedRealtime() < end) {
            if (reader.ready()) {
                int c = reader.read();
                if (c < 0) break;
                char ch = (char)c;
                gotAny = true;
                if (ch == '>') break;
                sb.append(ch);
            } else {
                if (gotAny && sb.length() > 0) Thread.sleep(1); else Thread.sleep(2);
            }
        }
        return sb.toString().replace("\r", " ").replace("\n", " ").trim();
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
            int C = d.length() >= 6 ? Integer.parseInt(d.substring(4,6),16) : 0;
            int D = d.length() >= 8 ? Integer.parseInt(d.substring(6,8),16) : 0;
            switch (cmd) {
                case "010C": return ((A * 256) + B) / 4.0;
                case "010D": return (double)A;
                case "0106":
                case "0107":
                case "0156": return A * 100.0 / 128.0 - 100.0;
                case "011F":
                case "0121":
                case "0131": return (double)((A * 256) + B);
                case "0123": return ((A * 256) + B) / 10.0;
                case "012E": return A * 100.0 / 255.0;
                case "0130": return (double)A;
                case "0134": return ((A * 256) + B) * 2.0 / 65536.0;
                case "013C": return ((A * 256) + B) / 10.0 - 40.0;
                case "0143": return ((A * 256) + B) * 100.0 / 255.0;
                case "0144": return ((A * 256) + B) * 2.0 / 65536.0;
                case "0105":
                case "010F":
                case "0146": return (double)A - 40.0;
                case "010B":
                case "0133": return (double)A;
                case "0111":
                case "0104":
                case "0145":
                case "0147":
                case "0149":
                case "014A":
                case "014C": return A * 100.0 / 255.0;
                case "010E": return A / 2.0 - 64.0;
                case "0142": return ((A * 256) + B) / 1000.0;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void updateTile(String label, String value, String unit) {
        ui.post(() -> {
            double numeric;
            try { numeric = Double.parseDouble(value.replace(',', '.')); } catch (Exception ex) { numeric = Double.NaN; }
            if (!Double.isNaN(numeric)) {
                liveValues.put(label, numeric);
                Double map = liveValues.get("Saugrohrdruck");
                Double baro = liveValues.get("Umgebungsdruck");
                if (map != null && baro != null) {
                    // PID 0B ist der Saugrohr-Absolutdruck hinter der Drosselklappe.
                    // Er ist beim CAVC NICHT identisch mit dem VAG-Ladedruck aus MWB 115.
                    liveValues.put("Saugrohrdruck rel.", (map - baro) / 100.0);
                }
                if (dashboardView != null) dashboardView.invalidate();
            }
            if (liveGrid == null) return;
            TextView found = null;
            for (int i=0;i<liveGrid.getChildCount();i++) {
                View v = liveGrid.getChildAt(i);
                if (v instanceof TextView && label.equals(v.getTag())) {
                    found = (TextView)v;
                    break;
                }
            }
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


    private class DashboardView extends View {
        private static final float BW = 1024f, BH = 600f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Bitmap masterCar;
        private final Map<String, Double> displayValues = new LinkedHashMap<>();
        private float sx=1f, sy=1f;
        private boolean animatorRunning = false;
        private int mfaPage = 0;
        private float touchDownX = -1f;
        private final Runnable animator = new Runnable() {
            @Override public void run() {
                if (!animatorRunning) return;
                for (Map.Entry<String, Double> e : liveValues.entrySet()) {
                    Double old = displayValues.get(e.getKey());
                    double target = e.getValue();
                    if (old == null) displayValues.put(e.getKey(), target);
                    else if ("Drehzahl".equals(e.getKey()) || "Geschwindigkeit".equals(e.getKey()) ||
                            "Saugrohrdruck".equals(e.getKey()) || "Saugrohrdruck rel.".equals(e.getKey()) || "Motorlast".equals(e.getKey()) ||
                            "Pedalstellung".equals(e.getKey()) || "Drosselklappe".equals(e.getKey())) {
                        // Schnell bewegte Anzeigen ohne zusätzliche UI-Glättung/Latenz.
                        displayValues.put(e.getKey(), target);
                    } else {
                        double next = old + (target - old) * 0.30;
                        if (Math.abs(target-next) < 0.05) next = target;
                        displayValues.put(e.getKey(), next);
                    }
                }
                invalidate();
                postDelayed(this, 16); // ~60 FPS UI interpolation
            }
        };

        DashboardView() {
            super(MainActivity.this);
            masterCar = BitmapFactory.decodeResource(getResources(), de.growcentral.touranlive.R.drawable.touran_master_final);
            setBackgroundColor(Color.rgb(3,5,7));
            setFocusable(true);
        }

        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow(); animatorRunning = true; post(animator);
        }
        @Override protected void onDetachedFromWindow() {
            animatorRunning = false; removeCallbacks(animator); super.onDetachedFromWindow();
        }

        private Double v(String key) {
            Double d = displayValues.get(key);
            return d != null ? d : liveValues.get(key);
        }
        private Double vAny(String... keys) {
            for (String key : keys) { Double d=v(key); if (d!=null) return d; }
            return null;
        }
        private String n(String key) { Double d=v(key); return d==null?"—":fmt(d); }
        private String val(String key,String unit) { Double d=v(key); return d==null?"—":fmt(d)+(unit.isEmpty()?"":" "+unit); }
        private String valAny(String unit,String... keys) { Double d=vAny(keys); return d==null?"—":fmt(d)+(unit.isEmpty()?"":" "+unit); }
        private float X(float x){ return x*sx; }
        private float Y(float y){ return y*sy; }
        private float S(float a){ return a*Math.min(sx,sy); }

        private void fill(Canvas c,int color,float l,float t,float r,float b){
            p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawRect(X(l),Y(t),X(r),Y(b),p);
        }
        private void round(Canvas c,int color,float l,float t,float r,float b,float rad){
            p.setStyle(Paint.Style.FILL); p.setColor(color); c.drawRoundRect(new RectF(X(l),Y(t),X(r),Y(b)),S(rad),S(rad),p);
        }
        private void strokeRound(Canvas c,int color,float l,float t,float r,float b,float rad,float sw){
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(S(sw)); p.setColor(color); c.drawRoundRect(new RectF(X(l),Y(t),X(r),Y(b)),S(rad),S(rad),p);
        }
        private void line(Canvas c,int color,float sw,float x1,float y1,float x2,float y2){
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(S(sw)); p.setColor(color); c.drawLine(X(x1),Y(y1),X(x2),Y(y2),p);
        }
        private void txt(Canvas c,String s,float x,float y,float size,int color,Paint.Align align,boolean bold){
            p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(S(size)); p.setTextAlign(align);
            p.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT); c.drawText(s,X(x),Y(y),p);
        }

        private void gauge(Canvas c,float cx,float cy,float r,double max,String key,boolean rpm){
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(4,6,8)); c.drawCircle(X(cx),Y(cy),S(r),p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(S(10)); p.setColor(Color.rgb(35,39,43)); c.drawCircle(X(cx),Y(cy),S(r),p);
            p.setStrokeWidth(S(3)); p.setColor(Color.rgb(205,210,214)); c.drawCircle(X(cx),Y(cy),S(r-8),p);
            p.setStrokeWidth(S(2)); p.setColor(Color.rgb(95,100,105)); c.drawCircle(X(cx),Y(cy),S(r-17),p);
            float start=140f,sweep=260f;
            int ticks=rpm?40:48;
            for(int i=0;i<=ticks;i++){
                float f=i/(float)ticks, a=(float)Math.toRadians(start+sweep*f);
                boolean major=i%(rpm?5:4)==0;
                boolean red=rpm && f>.72f;
                float ro=r-25, ri=ro-(major?23:12);
                line(c,red?RED:TEXT,major?3.2f:1.8f,
                    cx+(float)Math.cos(a)*ri,cy+(float)Math.sin(a)*ri,
                    cx+(float)Math.cos(a)*ro,cy+(float)Math.sin(a)*ro);
                if(major){
                    int number=rpm?i/5:(int)Math.round(240.0*(i/(float)ticks)/20.0)*20;
                    if(!rpm) number=(i/4)*20;
                    txt(c,String.valueOf(number),cx+(float)Math.cos(a)*(r-62),cy+(float)Math.sin(a)*(r-62)+8,rpm?23:20,TEXT,Paint.Align.CENTER,true);
                }
            }
            if(rpm) txt(c,"1/min x 1000",cx,cy-112,18,MUTED,Paint.Align.CENTER,false);
            else txt(c,"km/h",cx,cy-105,18,MUTED,Paint.Align.CENTER,false);
            Double d=v(key); double q=d==null?0:Math.max(0,Math.min(max,d));
            float a=(float)Math.toRadians(start+sweep*(q/max));
            line(c,Color.rgb(255,25,31),12,cx,cy,cx+(float)Math.cos(a)*(r-78),cy+(float)Math.sin(a)*(r-78));
            line(c,Color.rgb(255,95,80),3,cx,cy,cx+(float)Math.cos(a)*(r-74),cy+(float)Math.sin(a)*(r-74));
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(12,14,17));c.drawCircle(X(cx),Y(cy),S(37),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(S(3));p.setColor(Color.rgb(160,165,170));c.drawCircle(X(cx),Y(cy),S(37),p);
            round(c,Color.rgb(7,9,12),cx-108,cy+72,cx+108,cy+147,7); strokeRound(c,Color.rgb(55,60,66),cx-108,cy+72,cx+108,cy+147,7,2);
            txt(c,n(key),cx,cy+119,34,TEXT,Paint.Align.CENTER,true);
            txt(c,rpm?"rpm":"km/h",cx,cy+143,18,MUTED,Paint.Align.CENTER,false);
        }

        private void miniGauge(Canvas c,float cx,float cy,float r,String title,String key,String unit,double min,double max){
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(4,6,8));c.drawCircle(X(cx),Y(cy),S(r),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(S(3));p.setColor(Color.rgb(130,135,140));c.drawCircle(X(cx),Y(cy),S(r),p);
            float start=145f,sweep=250f;
            for(int i=0;i<=10;i++){
                float a=(float)Math.toRadians(start+sweep*i/10f); float ro=r-8,ri=ro-(i%5==0?14:8);
                line(c,TEXT,i%5==0?2.3f:1.2f,cx+(float)Math.cos(a)*ri,cy+(float)Math.sin(a)*ri,cx+(float)Math.cos(a)*ro,cy+(float)Math.sin(a)*ro);
            }
            Double d=v(key); double q=d==null?min:Math.max(min,Math.min(max,d)); float f=(float)((q-min)/(max-min)); float a=(float)Math.toRadians(start+sweep*f);
            line(c,RED,6,cx,cy,cx+(float)Math.cos(a)*(r-28),cy+(float)Math.sin(a)*(r-28));
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(18,20,23));c.drawCircle(X(cx),Y(cy),S(12),p);
            txt(c,val(key,unit),cx,cy+44,20,TEXT,Paint.Align.CENTER,true); txt(c,title,cx,cy+69,14,MUTED,Paint.Align.CENTER,false);
        }

        private void miniGaugeAny(Canvas c,float cx,float cy,float r,String title,String unit,double min,double max,String... keys){
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(4,6,8));c.drawCircle(X(cx),Y(cy),S(r),p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(S(3));p.setColor(Color.rgb(130,135,140));c.drawCircle(X(cx),Y(cy),S(r),p);
            float start=145f,sweep=250f;
            for(int i=0;i<=10;i++){ float a=(float)Math.toRadians(start+sweep*i/10f); float ro=r-8,ri=ro-(i%5==0?14:8); line(c,TEXT,i%5==0?2.3f:1.2f,cx+(float)Math.cos(a)*ri,cy+(float)Math.sin(a)*ri,cx+(float)Math.cos(a)*ro,cy+(float)Math.sin(a)*ro); }
            Double d=vAny(keys); double q=d==null?min:Math.max(min,Math.min(max,d)); float f=(float)((q-min)/(max-min)); float a=(float)Math.toRadians(start+sweep*f);
            line(c,RED,6,cx,cy,cx+(float)Math.cos(a)*(r-28),cy+(float)Math.sin(a)*(r-28)); p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(18,20,23));c.drawCircle(X(cx),Y(cy),S(12),p);
            txt(c,valAny(unit,keys),cx,cy+44,20,TEXT,Paint.Align.CENTER,true); txt(c,title,cx,cy+69,14,MUTED,Paint.Align.CENTER,false);
        }

        private void mfaRow(Canvas c,float y,String label,String key,String unit){
            line(c,Color.rgb(50,54,60),1.3f,608,y+28,1054,y+28);
            txt(c,label,668,y+4,18,MUTED,Paint.Align.LEFT,false);
            txt(c,val(key,unit),1028,y+4,21,TEXT,Paint.Align.RIGHT,true);
        }

        private void drawMasterCar(Canvas c){
            if (masterCar == null) return;
            RectF dst = new RectF(X(590),Y(430),X(1074),Y(704));
            p.setAlpha(245);
            c.drawBitmap(masterCar, null, dst, p);
            p.setAlpha(255);
        }

        private void nav(Canvas c,float l,float r,String label,boolean active){
            round(c,active?Color.rgb(25,9,11):Color.rgb(9,12,15),l,812,r,916,10);
            strokeRound(c,active?RED:Color.rgb(42,47,53),l,812,r,916,10,active?2.5f:1.5f);
            txt(c,label,(l+r)/2,876,22,active?TEXT:Color.rgb(195,199,204),Paint.Align.CENTER,true);
        }

        @Override protected void onDraw(Canvas c){
            super.onDraw(c); sx=getWidth()/BW; sy=getHeight()/BH;
            fill(c,Color.rgb(2,4,7),0,0,1024,600);
            fill(c,Color.rgb(5,8,11),0,0,1024,50); line(c,RED,2,195,49,815,49);
            txt(c,"VW",31,31,16,TEXT,Paint.Align.CENTER,true); txt(c,"135er Touran Live",62,23,21,TEXT,Paint.Align.LEFT,true);
            txt(c,"VW Touran 1T3 | CAVC 1.4 TSI 140 PS",62,42,12,MUTED,Paint.Align.LEFT,false);
            txt(c,"Fahrzeugansicht (MFA) - Seite "+(mfaPage+1)+"/4",512,31,17,TEXT,Paint.Align.CENTER,true);
            txt(c,polling?(vagMode?"VAG verbunden":"OBD verbunden"):"getrennt",865,22,14,polling?OK:RED,Paint.Align.RIGHT,true);
            txt(c,polling?protocolName:"-",865,40,11,MUTED,Paint.Align.RIGHT,false);
            txt(c,new SimpleDateFormat("HH:mm",Locale.GERMANY).format(new Date()),1000,23,18,TEXT,Paint.Align.RIGHT,true);
            txt(c,new SimpleDateFormat("dd.MM.yyyy",Locale.GERMANY).format(new Date()),1000,41,11,MUTED,Paint.Align.RIGHT,false);
            gauge(c,160,195,145,8000,"Drehzahl",true); gauge(c,864,195,145,240,"Geschwindigkeit",false);
            miniGaugeAny(c,80,365,62,"K\u00fchlmittel","\u00b0C",50,130,"VAG_Coolant","K\u00fchlmittel");
            miniGaugeAny(c,245,365,62,"\u00d6ltemperatur","\u00b0C",50,150,"VAG_OilTemp");
            miniGaugeAny(c,779,365,62,"Bordspannung","V",10,16,"VAG_Voltage","ECU-Spannung");
            miniGaugeAny(c,944,365,62,"Au\u00dfentemperatur","\u00b0C",-20,50,"Au\u00dfentemperatur");
            round(c,Color.rgb(7,10,14),326,60,698,270,10); strokeRound(c,Color.rgb(45,50,56),326,60,698,270,10,1.5f);
            txt(c,"<",347,92,26,TEXT,Paint.Align.CENTER,true); txt(c,(mfaPage+1)+"/4",512,90,18,TEXT,Paint.Align.CENTER,true); txt(c,">",676,92,26,TEXT,Paint.Align.CENTER,true); line(c,RED,2,340,102,684,102);
            if(mfaPage==0){
                masterBoostRow(c,122);
                masterRowAny(c,166,"Motorlast","%","Motorlast","Absolute Last");
                masterRowAny(c,188,"Luftmasse","g/s","VAG_MAF");
                masterRowAny(c,210,"Drosselklappe","%","VAG_ThrottleRel","Drosselklappe");
                masterRowAny(c,232,"Gaspedalstellung","%","Pedalstellung","Pedalstellung E");
                masterRowAny(c,254,"Zündwinkel","°","VAG_Ignition","Zündwinkel");
            } else if(mfaPage==1){
                masterRowAny(c,126,"\u00d6ltemperatur","\u00b0C","VAG_OilTemp");
                masterRowAny(c,158,"K\u00fchlmittel","\u00b0C","VAG_Coolant","K\u00fchlmittel");
                masterRowAny(c,190,"Ansaugluft","\u00b0C","VAG_IntakeTemp","Ansaugluft");
                masterRowAny(c,222,"Bordspannung","V","VAG_Voltage","ECU-Spannung");
                masterRowAny(c,254,"Au\u00dfentemperatur","\u00b0C","Au\u00dfentemperatur");
            } else if(mfaPage==2){
                masterRowAny(c,126,"Lambda Ist","λ","VAG_LambdaActual","Lambda Ist");
                masterRowAny(c,158,"Lambda Soll","λ","VAG_LambdaTarget","Lambda Soll");
                masterRowAny(c,190,"STFT / Adapt. kurz","%","VAG_AdaptIdle","Fuel Trim kurz");
                masterRowAny(c,222,"LTFT / Adapt. lang","%","VAG_AdaptPart","Fuel Trim lang");
                masterRowAny(c,254,"Kat-Temperatur","\u00b0C","Kat-Temperatur");
            } else {
                masterRowAny(c,126,"Z\u00fcndwinkel","\u00b0","VAG_Ignition","Z\u00fcndwinkel");
                masterRowAny(c,158,"Nockenwelle Soll","\u00b0KW","VAG_CamTarget");
                masterRowAny(c,190,"Nockenwelle Ist","\u00b0KW","VAG_CamActual");
                masterRowAny(c,222,"Luftmasse","g/s","VAG_MAF");
                masterRowAny(c,254,"Drosselklappe","%","Drosselklappe","VAG_ThrottleRel");
            }
            round(c,Color.rgb(5,7,10),305,278,719,463,8);
            if(masterCar!=null){ RectF dst=new RectF(X(330),Y(286),X(694),Y(455)); p.setAlpha(255); c.drawBitmap(masterCar,null,dst,p); }
            txt(c,"VW Touran 1T3",32,447,17,TEXT,Paint.Align.LEFT,true); txt(c,"CAVC | B-JU 6969",32,466,12,MUTED,Paint.Align.LEFT,false);
            fill(c,Color.rgb(5,8,10),0,470,1024,523); line(c,Color.rgb(48,53,59),1,0,470,1024,470);
            txt(c,polling?(vagMode?"VAG verbunden":"OBD verbunden"):"OBD getrennt",34,494,15,polling?OK:RED,Paint.Align.LEFT,true); txt(c,polling?protocolName:"-",34,513,11,MUTED,Paint.Align.LEFT,false);
            txt(c,"OBD Fallback",270,494,14,TEXT,Paint.Align.LEFT,true); txt(c,vagMode?"bereit":"aktiv",270,513,11,vagMode?MUTED:OK,Paint.Align.LEFT,false);
            txt(c,"Logger",500,494,14,TEXT,Paint.Align.LEFT,true); txt(c,logRows+" Zeilen",500,513,11,MUTED,Paint.Align.LEFT,false);
            int dc="Keine Fehler gemeldet".equals(dtcStatus)?OK:MUTED; txt(c,"DTC",735,494,14,TEXT,Paint.Align.LEFT,true); txt(c,dtcStatus,735,513,11,dc,Paint.Align.LEFT,false);
            masterNav(c,0,170,"Tacho",true); masterNav(c,171,340,"Live",false); masterNav(c,341,510,"Diagnose",false); masterNav(c,511,680,"Logger",false); masterNav(c,681,850,"Apps",false); masterNav(c,851,1024,"VCDS",false);
        }
        private void masterRow(Canvas c,float y,String label,String key,String unit){ line(c,Color.rgb(40,45,51),1,340,y+14,684,y+14); txt(c,label,346,y,13,MUTED,Paint.Align.LEFT,false); txt(c,val(key,unit),678,y,15,TEXT,Paint.Align.RIGHT,true); }
        private void masterRowAny(Canvas c,float y,String label,String unit,String... keys){ line(c,Color.rgb(40,45,51),1,340,y+14,684,y+14); txt(c,label,346,y,13,MUTED,Paint.Align.LEFT,false); txt(c,valAny(unit,keys),678,y,15,TEXT,Paint.Align.RIGHT,true); }
        private void masterBoostRow(Canvas c,float y){
            line(c,Color.rgb(40,45,51),1,340,y+31,684,y+31);
            txt(c,"Ladedruck",346,y,13,MUTED,Paint.Align.LEFT,false);
            txt(c,valAny("bar","VAG_BoostActualRel"),678,y,18,TEXT,Paint.Align.RIGHT,true);
            txt(c,"Soll",346,y+18,11,MUTED,Paint.Align.LEFT,false);
            txt(c,valAny("bar","VAG_BoostTargetRel"),678,y+18,12,MUTED,Paint.Align.RIGHT,false);
        }
        private void masterNav(Canvas c,float l,float r,String label,boolean active){ round(c,active?Color.rgb(28,7,9):Color.rgb(7,10,13),l+4,527,r-4,596,7); strokeRound(c,active?RED:Color.rgb(42,47,53),l+4,527,r-4,596,7,active?2:1); txt(c,label,(l+r)/2,568,17,active?TEXT:Color.rgb(205,209,214),Paint.Align.CENTER,true); }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            float x=e.getX()/sx,y=e.getY()/sy; if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){touchDownX=x;return true;} if(e.getAction()!=android.view.MotionEvent.ACTION_UP)return true;
            if(y>=527){ if(x<170){invalidate();return true;} if(x<340){buildShell();showHome();return true;} if(x<510){buildShell();showDiagnostics();return true;} if(x<680){buildShell();showLogger();return true;} if(x<850){buildShell();showApps();return true;} buildShell();showVcds();return true; }
            if(y>=60&&y<=270&&x>=326&&x<=698){ float dx=x-touchDownX; if(Math.abs(dx)>45){mfaPage=(mfaPage+(dx<0?1:3))%4;invalidate();return true;} if(y<110&&x<390){mfaPage=(mfaPage+3)%4;invalidate();return true;} if(y<110&&x>635){mfaPage=(mfaPage+1)%4;invalidate();return true;} }
            if(y>=470&&y<523&&x<220){if(polling)disconnect();else connect();return true;} return true;
        }
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

    private void runCapabilityScanAndUpload() {
        if (socket == null || !socket.isConnected()) {
            toast("Zuerst OBD/VAG verbinden.");
            return;
        }
        toast("Adapter- und Daten-Scan l?uft ?");
        polling = false;
        io.execute(() -> {
            try {
                appendSystemLog("CAPABILITY_SCAN_BEGIN", "TouranLive read-only adapter scan");
                if (vagMode && vag != null && vag.isOpen()) {
                    int[] groups = {1,2,3,4,5,6,15,16,20,31,32,90,91,93,106,111,113,114,115,117,118,119,120,122,130,131,132,134,140,141,201,204,210,211,227,229};
                    for (int group : groups) {
                        try {
                            VagTp20.Block b = vag.readBlock(group);
                            appendSystemLog("CAP_VAG_" + group, b == null ? "NO_DATA" : b.raw);
                            if (b != null) applyVagBlock(b);
                        } catch (Exception e) {
                            appendSystemLog("CAP_VAG_" + group, "ERROR:" + safe(e.getMessage()));
                        }
                    }
                    appendSystemLog("CAPABILITY_SCAN_MODE", "VAG TP2.0/KWP2000");
                } else {
                    String[] adapter = {"ATI","AT@1","AT@2","ATDP","ATDPN","ATRV","0100","0120","0140","0160","0180","0900","0920"};
                    for (String cmd : adapter) {
                        try { appendSystemLog("CAP_" + cmd, send(cmd)); }
                        catch (Exception e) { appendSystemLog("CAP_" + cmd, "ERROR:" + safe(e.getMessage())); }
                    }
                    discoverSupportedPids();
                    appendSystemLog("CAPABILITY_SCAN_MODE", "Standard OBD/ELM327");
                }
                appendSystemLog("CAPABILITY_SCAN_END", "read-only");
                uploadCurrentLogInternal(true);
            } catch (Exception e) {
                appendSystemLog("CAPABILITY_SCAN_ERROR", safe(e.getMessage()));
                toast("Scan fehlgeschlagen: " + safe(e.getMessage()));
            } finally {
                if (socket != null && socket.isConnected()) {
                    polling = true;
                    if (vagMode && vag != null && vag.isOpen()) pollVagLoop();
                    else pollLoop();
                }
            }
        });
    }

    private void uploadCurrentLog(boolean capabilityReport) {
        netIo.execute(() -> uploadCurrentLogInternal(capabilityReport));
    }

    private void uploadCurrentLogInternal(boolean capabilityReport) {
        HttpURLConnection con = null;
        try {
            String snapshot;
            synchronized (logLock) { snapshot = csvLog.toString(); }
            String installId = getInstallId();
            StringBuilder report = new StringBuilder();
            report.append("TouranLive\n");
            report.append("report_type=").append(capabilityReport ? "capability" : "log").append('\n');
            report.append("install_id=").append(installId).append('\n');
            report.append("vehicle=VW Touran 1T3 2012 CAVC MED17.5.5\n");
            report.append("protocol=").append(protocolName).append('\n');
            report.append("vag_mode=").append(vagMode).append('\n');
            report.append("generated=").append(now()).append('\n');
            report.append("--- CSV LOG ---\n").append(snapshot);

            URL url = new URL(LOG_UPLOAD_URL);
            con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(12000);
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            con.setRequestProperty("X-Touran-Install", installId);
            con.setRequestProperty("X-Touran-Report", capabilityReport ? "capability" : "log");
            byte[] bytes = report.toString().getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = con.getOutputStream()) { out.write(bytes); out.flush(); }
            int code = con.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
            String response = "";
            if (in != null) {
                byte[] data = new byte[4096]; int n; StringBuilder sb = new StringBuilder();
                while ((n = in.read(data)) > 0) sb.append(new String(data,0,n,StandardCharsets.UTF_8));
                response = sb.toString();
                in.close();
            }
            appendSystemLog("SERVER_UPLOAD", "HTTP " + code + " " + response);
            if (code >= 200 && code < 300) toast(capabilityReport ? "Adapter-Scan an dezender.de gesendet." : "Log an dezender.de gesendet.");
            else toast("Server-Upload fehlgeschlagen: HTTP " + code);
        } catch (Exception e) {
            appendSystemLog("SERVER_UPLOAD_ERROR", safe(e.getMessage()));
            toast("Server-Upload fehlgeschlagen: " + safe(e.getMessage()));
        } finally {
            if (con != null) con.disconnect();
        }
    }

    private String getInstallId() {
        android.content.SharedPreferences p = getSharedPreferences("touranlive", MODE_PRIVATE);
        String id = p.getString("install_id", null);
        if (id == null || id.length() < 8) {
            id = UUID.randomUUID().toString();
            p.edit().putString("install_id", id).apply();
        }
        return id;
    }

    private void runRadioSystemScanAndSend() {
        netIo.execute(() -> {
            try {
                String report = buildRadioSystemReport();
                boolean ok = uploadReport("radio-system", report);
                toast(ok ? "Radio-Systemscan gesendet." : "Systemscan konnte nicht gesendet werden.");
            } catch (Exception e) {
                appendSystemLog("RADIO_SCAN_ERROR", safe(e.getMessage()));
                toast("Systemscan fehlgeschlagen: " + safe(e.getMessage()));
            }
        });
    }

    private String buildRadioSystemReport() {
        StringBuilder r = new StringBuilder();
        r.append("# TouranLive Radio System Report\n");
        r.append("app_version=").append(BuildConfig.VERSION_NAME).append('\n');
        r.append("timestamp=").append(now()).append('\n');
        r.append("manufacturer=").append(safe(Build.MANUFACTURER)).append('\n');
        r.append("brand=").append(safe(Build.BRAND)).append('\n');
        r.append("model=").append(safe(Build.MODEL)).append('\n');
        r.append("device=").append(safe(Build.DEVICE)).append('\n');
        r.append("product=").append(safe(Build.PRODUCT)).append('\n');
        r.append("board=").append(safe(Build.BOARD)).append('\n');
        r.append("hardware=").append(safe(Build.HARDWARE)).append('\n');
        r.append("bootloader=").append(safe(Build.BOOTLOADER)).append('\n');
        r.append("android_release=").append(safe(Build.VERSION.RELEASE)).append('\n');
        r.append("sdk=").append(Build.VERSION.SDK_INT).append('\n');
        r.append("fingerprint=").append(safe(Build.FINGERPRINT)).append('\n');
        r.append("abis=").append(Arrays.toString(Build.SUPPORTED_ABIS)).append('\n');
        try { r.append("baseband=").append(safe(Build.getRadioVersion())).append('\n'); } catch (Exception ignored) {}

        DisplayMetrics dm = getResources().getDisplayMetrics();
        r.append("display_px=").append(dm.widthPixels).append('x').append(dm.heightPixels).append('\n');
        r.append("density_dpi=").append(dm.densityDpi).append('\n');
        r.append("density=").append(dm.density).append('\n');
        try {
            android.view.Display.Mode m = getWindowManager().getDefaultDisplay().getMode();
            r.append("refresh_hz=").append(m.getRefreshRate()).append('\n');
        } catch (Exception ignored) {}

        StatFs sf = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long total = sf.getTotalBytes(), free = sf.getAvailableBytes();
        r.append("data_total_bytes=").append(total).append('\n');
        r.append("data_free_bytes=").append(free).append('\n');

        r.append("feature_usb_host=").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)).append('\n');
        r.append("feature_bt=").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)).append('\n');
        r.append("feature_ble=").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)).append('\n');
        r.append("feature_wifi=").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)).append('\n');
        r.append("feature_gps=").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)).append('\n');
        r.append("feature_touch=").append(getPackageManager().hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)).append('\n');
        r.append("vehicle_profile=").append(VehicleProfile.MODEL).append(" | ").append(VehicleProfile.ENGINE_TEXT).append(" | ").append(VehicleProfile.GEARBOX).append('\n');
        r.append("production_date=").append(VehicleProfile.PRODUCTION_DATE).append('\n');
        r.append("sales_type=").append(VehicleProfile.SALES_TYPE).append('\n');
        r.append("battery=").append(VehicleProfile.BATTERY).append(" generator=").append(VehicleProfile.GENERATOR).append('\n');
        r.append("factory_radio=").append(VehicleProfile.FACTORY_RADIO).append('\n');
        r.append("equipment_profile=\n").append(VehicleProfile.compactEquipmentSummary()).append('\n');

        r.append("\n## /proc/cpuinfo\n").append(readSmallFile("/proc/cpuinfo", 18000));
        r.append("\n## /proc/meminfo\n").append(readSmallFile("/proc/meminfo", 12000));
        r.append("\n## /proc/partitions\n").append(readSmallFile("/proc/partitions", 12000));
        r.append("\n## /proc/mounts\n").append(readSmallFile("/proc/mounts", 24000));
        r.append("\n## getprop (filtered)\n").append(runFilteredGetprop());
        r.append("\n## headunit packages\n").append(findHeadunitPackages());
        return r.toString();
    }

    private String readSmallFile(String path, int maxChars) {
        StringBuilder out = new StringBuilder();
        try (FileInputStream in = new FileInputStream(path)) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0 && out.length() < maxChars) out.append(new String(b,0,n,StandardCharsets.UTF_8));
        } catch (Exception e) { out.append("UNREADABLE: ").append(safe(e.getMessage())); }
        if (out.length() > maxChars) out.setLength(maxChars);
        return out.toString();
    }

    private String runFilteredGetprop() {
        StringBuilder out = new StringBuilder();
        try {
            Process p = new ProcessBuilder("getprop").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String l = line.toLowerCase(Locale.ROOT);
                    if (l.contains("serial") || l.contains("imei") || l.contains("mac") || l.contains("android_id")) continue;
                    if (l.contains("ro.product") || l.contains("ro.build") || l.contains("ro.hardware") || l.contains("ro.boot") ||
                        l.contains("ro.vendor") || l.contains("ro.system") || l.contains("ro.soc") || l.contains("ro.sf.lcd_density") ||
                        l.contains("persist.sys") || l.contains("mcu") || l.contains("canbus")) out.append(line).append('\n');
                }
            }
        } catch (Exception e) { out.append("getprop unavailable: ").append(safe(e.getMessage())); }
        return out.toString();
    }

    private String findHeadunitPackages() {
        StringBuilder out = new StringBuilder();
        for (ApplicationInfo ai : getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA)) {
            String pkg = ai.packageName == null ? "" : ai.packageName;
            String l = pkg.toLowerCase(Locale.ROOT);
            if (l.contains("radio") || l.contains("mcu") || l.contains("canbus") || l.contains("zlink") || l.contains("tlink") ||
                l.contains("carlink") || l.contains("autokit") || l.contains("bluetooth") || l.contains("launcher") || l.contains("eq") ||
                l.contains("dsp") || l.contains("vehicle") || l.contains("factory")) {
                CharSequence label = getPackageManager().getApplicationLabel(ai);
                out.append(pkg).append(" | ").append(label == null ? "" : label).append('\n');
            }
        }
        return out.toString();
    }

    private boolean uploadReport(String type, String body) {
        java.net.HttpURLConnection c = null;
        try {
            java.net.URL u = new java.net.URL("https://www.dezender.de/touran/api/upload-log.php");
            c = (java.net.HttpURLConnection)u.openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setConnectTimeout(10000); c.setReadTimeout(15000);
            c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
            c.setRequestProperty("X-Touran-Report", type);
            c.setRequestProperty("X-Touran-Install", getPackageName());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = c.getOutputStream()) { os.write(bytes); }
            int code = c.getResponseCode();
            appendSystemLog("SERVER_UPLOAD_" + type.toUpperCase(Locale.ROOT), "HTTP " + code);
            return code >= 200 && code < 300;
        } catch (Exception e) {
            appendSystemLog("SERVER_UPLOAD_ERROR", safe(e.getMessage())); return false;
        } finally { if (c != null) c.disconnect(); }
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
        ui.post(() -> {
            liveValues.clear();
            if (dashboardView != null) dashboardView.invalidate();
        });
        io.execute(() -> {
            appendSystemLog("DISCONNECTED", "manual");
            closeSocket();
            setConnectionState("OBD: getrennt", RED);
            postStatus("OBD getrennt. Der Log bleibt zum Export erhalten.");
        });
    }

    private void closeSocket() {
        try { if (vag != null) vag.close(); } catch (Exception ignored) {}
        vag = null;
        vagMode = false;
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
            if (dashboardView != null) dashboardView.invalidate();
        });
    }

    private void updateLogBadge() {
        ui.post(() -> {
            if (loggerBadge != null) loggerBadge.setText("LOG: " + logRows);
            if (dashboardView != null) dashboardView.invalidate();
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
