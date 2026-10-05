from pathlib import Path
p=Path(r'C:\Users\dezen\Documents\GitHub\VW-Touran-1T3-2012-Torque-Plugin\android-app\app\src\main\java\de\growcentral\touranlive\MainActivity.java')
s=p.read_text(encoding='utf-8-sig')
old='''    private void pollLoop() {
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
'''
new='''    private void pollLoop() {
        // Priorisierte Abfrage: RPM + Speed in jedem Zyklus, dynamische Motorwerte rotierend,
        // Temperaturen/Spannung deutlich seltener. So bleiben die Instrumente fluessig,
        // ohne den ELM327 mit allen PIDs pro Runde zu blockieren.
        final String[] dynamic = {"010B","0104","0110","0111","0149","010E"};
        final String[] medium  = {"014C","0162","0163","015E","012F"};
        final String[] slow    = {"0105","015C","010F","0146","0142","0133"};
        int cycle = 0, dyn = 0, med = 0, slw = 0;
        while (polling && socket != null && socket.isConnected()) {
            pollPid("010C"); // Drehzahl - hoechste Prioritaet
            if (!polling) break;
            pollPid("010D"); // Geschwindigkeit - hoechste Prioritaet
            if (!polling) break;

            pollPid(dynamic[dyn++ % dynamic.length]);
            if ((cycle & 1) == 0) pollPid(medium[med++ % medium.length]);
            if (cycle % 8 == 0) pollPid(slow[slw++ % slow.length]);
            cycle++;
        }
    }

    private void pollPid(String cmd) {
        if (!polling || !Boolean.TRUE.equals(support.get(cmd))) return;
        Pid target = null;
        for (Pid p : pids) if (p.cmd.equals(cmd)) { target = p; break; }
        if (target == null) return;
        try {
            String raw = sendLive(cmd);
            Double value = decode(cmd, raw);
            String state = classify(raw, value);
            appendPidLog(target, raw, value, state);
            if (value != null) updateTile(target.label, fmt(value), target.unit);
        } catch (Exception e) {
            appendPidLog(target, "", null, "ERROR:" + safe(e.getMessage()));
        }
    }

    private String sendLive(String cmd) throws Exception {
        writer.write((cmd + "\\r").getBytes(StandardCharsets.US_ASCII));
        writer.flush();
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + 700;
        while (System.currentTimeMillis() < end) {
            if (reader.ready()) {
                int c = reader.read();
                if (c < 0) break;
                char ch = (char)c;
                if (ch == '>') break;
                sb.append(ch);
            } else {
                Thread.sleep(5);
            }
        }
        return sb.toString().replace("\\r", " ").replace("\\n", " ").trim();
    }
'''
if old not in s: raise SystemExit('pollLoop block not found')
s=s.replace(old,new,1)
# Dashboard animation state
old2='''        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float sx=1f, sy=1f;

        DashboardView() {
            super(MainActivity.this);
            setBackgroundColor(Color.rgb(3,5,7));
            setFocusable(true);
        }

        private Double v(String key) { return liveValues.get(key); }
'''
new2='''        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Map<String, Double> displayValues = new LinkedHashMap<>();
        private float sx=1f, sy=1f;
        private boolean animatorRunning = false;
        private final Runnable animator = new Runnable() {
            @Override public void run() {
                if (!animatorRunning) return;
                for (Map.Entry<String, Double> e : liveValues.entrySet()) {
                    Double old = displayValues.get(e.getKey());
                    double target = e.getValue();
                    if (old == null) displayValues.put(e.getKey(), target);
                    else {
                        double factor = ("Drehzahl".equals(e.getKey()) || "Geschwindigkeit".equals(e.getKey()) ||
                                "Saugrohrdruck".equals(e.getKey()) || "Ladedruck".equals(e.getKey()) ||
                                "Motorlast".equals(e.getKey())) ? 0.38 : 0.22;
                        double next = old + (target - old) * factor;
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
'''
if old2 not in s: raise SystemExit('dashboard block not found')
s=s.replace(old2,new2,1)
p.write_text(s,encoding='utf-8')
print('patched')