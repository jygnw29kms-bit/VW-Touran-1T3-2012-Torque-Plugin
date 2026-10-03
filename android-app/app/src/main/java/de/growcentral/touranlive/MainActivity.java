package de.growcentral.touranlive;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private BluetoothSocket socket;
    private BufferedReader reader;
    private OutputStream writer;
    private TextView status;
    private GridLayout grid;
    private volatile boolean polling = false;

    private static class Pid {
        final String cmd, label, unit;
        Pid(String cmd, String label, String unit) { this.cmd=cmd; this.label=label; this.unit=unit; }
    }

    private final Pid[] pids = new Pid[] {
        new Pid("010C","Drehzahl","rpm"),
        new Pid("010D","Geschwindigkeit","km/h"),
        new Pid("0105","Kühlmittel","°C"),
        new Pid("010F","Ansaugluft","°C"),
        new Pid("010B","MAP","kPa"),
        new Pid("0110","Luftmasse","g/s"),
        new Pid("0111","Drosselklappe","%"),
        new Pid("0104","Motorlast","%"),
        new Pid("010E","Zündwinkel","°"),
        new Pid("0106","STFT B1","%"),
        new Pid("0107","LTFT B1","%"),
        new Pid("012F","Tank","%"),
        new Pid("0133","Umgebungsdruck","kPa"),
        new Pid("0142","ECU-Spannung","V"),
        new Pid("0146","Außentemperatur","°C"),
        new Pid("0149","Pedal D","%"),
        new Pid("014C","Drossel Soll","%"),
        new Pid("015C","Öltemperatur","°C"),
        new Pid("015E","Kraftstoffrate","L/h"),
        new Pid("0162","Drehmoment Ist","%"),
        new Pid("0163","Referenzmoment","Nm")
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        requestBtPermission();
    }

    private void buildUi() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,18,18,18);
        root.setBackgroundColor(Color.rgb(5,7,10));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this);
        title.setText("TOURAN LIVE  •  1T3 2012  •  CAVC");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setPadding(6,4,18,8);
        top.addView(title,new LinearLayout.LayoutParams(0,-2,1));

        Button connect=new Button(this);
        connect.setText("OBDII verbinden");
        connect.setOnClickListener(v->connect());
        top.addView(connect);

        Button disconnect=new Button(this);
        disconnect.setText("Trennen");
        disconnect.setOnClickListener(v->disconnect());
        top.addView(disconnect);
        root.addView(top);

        status=new TextView(this);
        status.setText("Bereit – Zündung an, ES359 einstecken und Bluetooth koppeln.");
        status.setTextColor(Color.rgb(80,180,255));
        status.setTextSize(15);
        status.setPadding(6,4,6,10);
        root.addView(status);

        ScrollView scroll=new ScrollView(this);
        grid=new GridLayout(this);
        grid.setColumnCount(4);
        scroll.addView(grid);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void requestBtPermission() {
        if (Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},10);
        }
    }

    private void connect() {
        io.execute(() -> {
            try {
                BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
                if (adapter==null || !adapter.isEnabled()) throw new Exception("Bluetooth ist aus.");
                BluetoothDevice target=null;
                Set<BluetoothDevice> bonded=adapter.getBondedDevices();
                for (BluetoothDevice d: bonded) {
                    String name=d.getName();
                    if (name!=null && (name.equalsIgnoreCase("OBDII") || name.toUpperCase().contains("OBD"))) { target=d; break; }
                }
                if (target==null) throw new Exception("Kein gekoppeltes OBDII-Gerät gefunden.");
                postStatus("Verbinde mit "+target.getName()+" …");
                socket=target.createRfcommSocketToServiceRecord(SPP_UUID);
                socket.connect();
                writer=socket.getOutputStream();
                reader=new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                initElm();
                polling=true;
                postStatus("Verbunden • ELM327 initialisiert • Live-Daten laufen");
                pollLoop();
            } catch(Exception e) {
                postStatus("Verbindung fehlgeschlagen: "+e.getMessage());
                closeSocket();
            }
        });
    }

    private void initElm() throws Exception {
        send("ATZ"); send("ATE0"); send("ATL0"); send("ATS0"); send("ATH0"); send("ATSP0");
    }

    private String send(String cmd) throws Exception {
        writer.write((cmd+"\r").getBytes(StandardCharsets.US_ASCII));
        writer.flush();
        StringBuilder sb=new StringBuilder();
        long end=System.currentTimeMillis()+1800;
        while(System.currentTimeMillis()<end) {
            if(reader.ready()) {
                int c=reader.read();
                if(c<0) break;
                char ch=(char)c;
                if(ch=='>') break;
                sb.append(ch);
            } else Thread.sleep(20);
        }
        return sb.toString().replace("\r"," ").replace("\n"," ").trim();
    }

    private void pollLoop() {
        while(polling && socket!=null && socket.isConnected()) {
            for(Pid p: pids) {
                if(!polling) break;
                try {
                    String raw=send(p.cmd);
                    Double value=decode(p.cmd, raw);
                    if(value!=null) updateTile(p.label, fmt(value), p.unit);
                } catch(Exception ignored) {}
            }
        }
    }

    private Double decode(String cmd,String raw) {
        String s=raw.replaceAll("[^0-9A-Fa-f]","").toUpperCase();
        String key="41"+cmd.substring(2);
        int i=s.indexOf(key);
        if(i<0) return null;
        String d=s.substring(i+4);
        try {
            int A=d.length()>=2?Integer.parseInt(d.substring(0,2),16):0;
            int B=d.length()>=4?Integer.parseInt(d.substring(2,4),16):0;
            switch(cmd) {
                case "010C": return ((A*256)+B)/4.0;
                case "010D": return (double)A;
                case "0105": case "010F": case "0146": case "015C": return (double)A-40;
                case "010B": case "0133": return (double)A;
                case "0110": return ((A*256)+B)/100.0;
                case "0111": case "0104": case "012F": case "0149": case "014C": return A*100.0/255.0;
                case "010E": return A/2.0-64.0;
                case "0106": case "0107": return (A-128)*100.0/128.0;
                case "0142": return ((A*256)+B)/1000.0;
                case "015E": return ((A*256)+B)/20.0;
                case "0162": return (double)A-125.0;
                case "0163": return (double)((A*256)+B);
            }
        } catch(Exception ignored) {}
        return null;
    }

    private String fmt(double v) {
        if(Math.abs(v)>=100 || Math.rint(v)==v) return String.format(java.util.Locale.GERMANY,"%.0f",v);
        return String.format(java.util.Locale.GERMANY,"%.1f",v);
    }

    private void updateTile(String label,String value,String unit) {
        ui.post(() -> {
            TextView found=null;
            for(int i=0;i<grid.getChildCount();i++) {
                TextView t=(TextView)grid.getChildAt(i);
                if(t.getTag()!=null && t.getTag().equals(label)) { found=t; break; }
            }
            String txt=label+"\n"+value+" "+unit;
            if(found!=null) found.setText(txt);
            else {
                TextView t=new TextView(this);
                t.setTag(label);
                t.setText(txt);
                t.setTextColor(Color.WHITE);
                t.setTextSize(20);
                t.setGravity(Gravity.CENTER);
                t.setPadding(14,20,14,20);
                t.setBackgroundColor(Color.rgb(18,28,40));
                GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
                lp.width=0; lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1,1f);
                lp.setMargins(6,6,6,6);
                grid.addView(t,lp);
            }
        });
    }

    private void postStatus(String s) { ui.post(() -> status.setText(s)); }

    private void disconnect() {
        polling=false;
        io.execute(() -> { closeSocket(); postStatus("Getrennt."); });
    }

    private void closeSocket() {
        try { if(socket!=null) socket.close(); } catch(Exception ignored) {}
        socket=null;
    }

    @Override protected void onDestroy() {
        polling=false;
        closeSocket();
        io.shutdownNow();
        super.onDestroy();
    }
}
