from pathlib import Path
p=Path(r'C:\Users\dezen\Documents\GitHub\VW-Touran-1T3-2012-Torque-Plugin\android-app\app\src\main\java\de\growcentral\touranlive\MainActivity.java')
s=p.read_text(encoding='utf-8-sig')
s=s.replace('        private boolean animatorRunning = false;\n','        private boolean animatorRunning = false;\n        private int mfaPage = 0;\n        private float touchDownX = -1f;\n')
old='''            txt(c,"Fahrzeugansicht (MFA) – Seite 1/4",832,42,24,Color.rgb(190,195,201),Paint.Align.CENTER,false);'''
new='''            txt(c,"Fahrzeugansicht (MFA) – Seite "+(mfaPage+1)+"/4",832,42,24,Color.rgb(190,195,201),Paint.Align.CENTER,false);'''
s=s.replace(old,new)
start=s.index('            round(c,Color.rgb(8,11,14),582,76,1082,699,16);')
end=s.index('            fill(c,Color.rgb(5,8,10),0,706,1664,798);',start)
block='''            round(c,Color.rgb(8,11,14),582,76,1082,699,16); strokeRound(c,Color.rgb(38,43,48),582,76,1082,699,16,2);
            String[] pageNames={"Fahrt","Temperaturen","Motor / Diagnose","Zündung / Zylinder"};
            txt(c,"‹",622,140,42,TEXT,Paint.Align.CENTER,true);
            txt(c,(mfaPage+1)+"/4",762,137,24,Color.rgb(195,199,204),Paint.Align.CENTER,false);
            txt(c,pageNames[mfaPage],837,137,28,TEXT,Paint.Align.CENTER,true);
            txt(c,"›",1040,140,42,TEXT,Paint.Align.CENTER,true);
            line(c,RED,3,605,156,1058,156);

            if(mfaPage==0){
                round(c,Color.rgb(12,15,18),608,171,834,368,8); strokeRound(c,Color.rgb(45,50,56),608,171,834,368,8,1.5f);
                round(c,Color.rgb(12,15,18),846,171,1055,368,8); strokeRound(c,Color.rgb(45,50,56),846,171,1055,368,8,1.5f);
                txt(c,"Ladedruck (Ist)",622,207,18,MUTED,Paint.Align.LEFT,false); txt(c,val("Ladedruck","bar"),721,276,34,TEXT,Paint.Align.CENTER,true);
                txt(c,"Soll",628,350,17,MUTED,Paint.Align.LEFT,false); txt(c,"—",780,350,18,TEXT,Paint.Align.CENTER,true);
                txt(c,"Motorlast",950,207,18,MUTED,Paint.Align.CENTER,false); txt(c,n("Motorlast"),950,286,44,TEXT,Paint.Align.CENTER,true); txt(c,"%",950,347,20,MUTED,Paint.Align.CENTER,false);
                mfaRow(c,405,"Luftmasse (MAF)","Luftmasse","g/s"); mfaRow(c,468,"Drosselklappe","Drosselklappe","%"); mfaRow(c,531,"Gaspedalstellung","Pedalstellung","%"); mfaRow(c,594,"Zündwinkel","Zündwinkel","°KW");
            } else if(mfaPage==1){
                round(c,Color.rgb(12,15,18),608,171,834,368,8); strokeRound(c,Color.rgb(45,50,56),608,171,834,368,8,1.5f);
                round(c,Color.rgb(12,15,18),846,171,1055,368,8); strokeRound(c,Color.rgb(45,50,56),846,171,1055,368,8,1.5f);
                txt(c,"Öltemperatur",721,207,18,MUTED,Paint.Align.CENTER,false); txt(c,val("Öltemperatur","°C"),721,286,38,TEXT,Paint.Align.CENTER,true);
                txt(c,"Kühlmittel",950,207,18,MUTED,Paint.Align.CENTER,false); txt(c,val("Kühlmittel","°C"),950,286,38,TEXT,Paint.Align.CENTER,true);
                mfaRow(c,405,"Ansaugluft","Ansaugluft","°C"); mfaRow(c,468,"Außentemperatur","Außentemperatur","°C"); mfaRow(c,531,"Bordspannung","ECU-Spannung","V"); mfaRow(c,594,"Umgebungsdruck","Umgebungsdruck","kPa");
            } else if(mfaPage==2){
                round(c,Color.rgb(12,15,18),608,171,834,368,8); strokeRound(c,Color.rgb(45,50,56),608,171,834,368,8,1.5f);
                round(c,Color.rgb(12,15,18),846,171,1055,368,8); strokeRound(c,Color.rgb(45,50,56),846,171,1055,368,8,1.5f);
                txt(c,"Kraftstoffrate",721,207,18,MUTED,Paint.Align.CENTER,false); txt(c,val("Kraftstoffrate","L/h"),721,286,34,TEXT,Paint.Align.CENTER,true);
                txt(c,"Drehmoment Ist",950,207,18,MUTED,Paint.Align.CENTER,false); txt(c,val("Drehmoment Ist","%"),950,286,34,TEXT,Paint.Align.CENTER,true);
                mfaRow(c,405,"Referenzmoment","Referenzmoment","Nm"); mfaRow(c,468,"Tankfüllstand","Tank","%"); mfaRow(c,531,"Drossel Soll","Drossel Soll","%"); mfaRow(c,594,"DTC Status","__NONE__","");
                txt(c,dtcStatus,1028,598,17,"Keine Fehler gemeldet".equals(dtcStatus)?OK:MUTED,Paint.Align.RIGHT,true);
            } else {
                round(c,Color.rgb(12,15,18),608,171,834,368,8); strokeRound(c,Color.rgb(45,50,56),608,171,834,368,8,1.5f);
                round(c,Color.rgb(12,15,18),846,171,1055,368,8); strokeRound(c,Color.rgb(45,50,56),846,171,1055,368,8,1.5f);
                txt(c,"Zündwinkel",721,207,18,MUTED,Paint.Align.CENTER,false); txt(c,val("Zündwinkel","°KW"),721,286,38,TEXT,Paint.Align.CENTER,true);
                txt(c,"Klopfrücknahme",950,207,18,MUTED,Paint.Align.CENTER,false); txt(c,"—",950,286,38,TEXT,Paint.Align.CENTER,true);
                mfaRow(c,405,"Zylinder 1","__NONE__",""); mfaRow(c,468,"Zylinder 2","__NONE__",""); mfaRow(c,531,"Zylinder 3","__NONE__",""); mfaRow(c,594,"Zylinder 4","__NONE__","");
            }
            for(int i=0;i<4;i++){ p.setStyle(Paint.Style.FILL); p.setColor(i==mfaPage?RED:Color.rgb(70,76,82)); c.drawCircle(X(790+i*31),Y(669),S(i==mfaPage?8:7),p); }

'''
s=s[:start]+block+s[end:]
old_touch='''        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()!=android.view.MotionEvent.ACTION_UP) return true;
            float x=e.getX()/sx,y=e.getY()/sy;
            if(y>=812){
                if(x<347){ invalidate(); return true; }
                if(x<664){ buildShell(); showHome(); return true; }
                if(x<981){ buildShell(); showDiagnostics(); return true; }
                if(x<1298){ buildShell(); showLogger(); return true; }
                if(x<1477){ buildShell(); showApps(); return true; }
                buildShell(); showVcds(); return true;
            }
            if(y>=706 && y<798 && x<430){ if(polling) disconnect(); else connect(); return true; }
            return true;
        }'''
new_touch='''        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            float x=e.getX()/sx,y=e.getY()/sy;
            if(e.getAction()==android.view.MotionEvent.ACTION_DOWN){ touchDownX=x; return true; }
            if(e.getAction()!=android.view.MotionEvent.ACTION_UP) return true;
            if(y>=812){
                if(x<347){ invalidate(); return true; }
                if(x<664){ buildShell(); showHome(); return true; }
                if(x<981){ buildShell(); showDiagnostics(); return true; }
                if(x<1298){ buildShell(); showLogger(); return true; }
                if(x<1477){ buildShell(); showApps(); return true; }
                buildShell(); showVcds(); return true;
            }
            if(y>=706 && y<798 && x<430){ if(polling) disconnect(); else connect(); return true; }
            if(y>=76 && y<=699 && x>=582 && x<=1082){
                float dx=x-touchDownX;
                if(Math.abs(dx)>70){ mfaPage=(mfaPage+(dx<0?1:3))%4; invalidate(); return true; }
                if(y<180 && x<700){ mfaPage=(mfaPage+3)%4; invalidate(); return true; }
                if(y<180 && x>970){ mfaPage=(mfaPage+1)%4; invalidate(); return true; }
            }
            return true;
        }'''
if old_touch not in s: raise SystemExit('touch block not found')
s=s.replace(old_touch,new_touch)
p.write_text(s,encoding='utf-8')
print('patched MFA pages')