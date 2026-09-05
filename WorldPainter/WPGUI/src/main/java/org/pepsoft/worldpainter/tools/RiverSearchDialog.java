package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.tools.scripts.RiverSearchResult;
import org.pepsoft.worldpainter.tools.scripts.RiverSearchSession;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Automatic river search is read-only until Apply; no temporary world layer is used. */
final class RiverSearchDialog extends WorldPainterDialog {
    RiverSearchDialog(App app,Dimension dimension,RiverPreset preset,int count,boolean smooth) {
        super(app);this.app=app;this.dimension=dimension;
        settings=new RiverSearchSession.Settings(count,preset.sourceWidth,preset.maximumWidth,preset.depth,
                smooth,dimension.getSeed(),RiverSearchSession.DEFAULT_BUDGET_MILLIS);
        setTitle("Nehir — Rota ara ve önizle");
        preview.setPreferredSize(new java.awt.Dimension(640,480));
        preview.setOpaque(true);preview.setBackground(new Color(35,38,42));
        status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);
        status.setText("Dengeli arama: en fazla 120 saniye. Önizleme dünyayı değiştirmez.\n"
                + "Turuncu: planlanan yatak; mavi: merkez hattı. Uygula öncesinde sonucu kontrol et.");
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        controls.add(search);controls.add(apply);controls.add(cancel);
        JPanel bottom=new JPanel(new BorderLayout(5,5));
        bottom.add(status,BorderLayout.NORTH);bottom.add(progress,BorderLayout.CENTER);bottom.add(controls,BorderLayout.SOUTH);
        getContentPane().setLayout(new BorderLayout(8,8));add(preview,BorderLayout.CENTER);add(bottom,BorderLayout.SOUTH);
        apply.setEnabled(false);
        search.addActionListener(e->search());apply.addActionListener(e->apply());
        cancel.addActionListener(e->{if(busy)cancelled.set(true);else dispose();});
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){
            if(busy){closeAfterWork=true;cancelled.set(true);}else dispose();
        }});
        pack();setLocationRelativeTo(app);
    }
    private void search() {
        if(busy)return;
        if(app.getWorld()!=dimension.getWorld()){status.setText("Açık dünya değişti; pencereyi yeniden açın.");return;}
        cancelled.set(false);busy=true;search.setEnabled(false);apply.setEnabled(false);preview.setIcon(null);
        cancel.setText("Aramayı iptal et");progress.setValue(0);status.setText("Vadi analizi hazırlanıyor… Dünya değiştirilmiyor.");
        session=new RiverSearchSession(dimension,settings,cancelled::get,(fraction,stage)->SwingUtilities.invokeLater(()->{
            progress.setValue((int)(fraction*100));status.setText(stage+" — Dünya değiştirilmiyor.");
        }),null);
        new SwingWorker<RiverSearchResult,Void>() {
            private BufferedImage image;
            @Override protected RiverSearchResult doInBackground(){
                RiverSearchResult result=session.search();
                if(result.canApply()&&!cancelled.get())image=renderPreview(dimension,result);
                return result;
            }
            @Override protected void done(){
                busy=false;search.setEnabled(true);cancel.setText("Kapat");progress.setValue(100);
                try {
                    RiverSearchResult result=get();
                    if(image!=null)preview.setIcon(new ImageIcon(image));
                    apply.setEnabled(result.canApply()&&!session.isStale()&&!cancelled.get());
                    status.setText(statusText(result)+(session.isStale()?"\nDünya değişti; yeniden arayın.":""));
                }catch(Exception failure){status.setText("Arama tamamlanamadı: "+message(failure));}
                if(closeAfterWork)dispose();
            }
        }.execute();
    }
    private void apply() {
        if(busy||session==null)return;
        if(app.getWorld()!=dimension.getWorld()||session.isStale()) {
            apply.setEnabled(false);status.setText("Dünya değişti; önizleme geçersiz. Yeniden rota arayın.");return;
        }
        cancelled.set(false);busy=true;apply.setEnabled(false);search.setEnabled(false);cancel.setText("İşlemi iptal et");
        status.setText("Doğrulanmış nehir uygulanıyor…");
        new SwingWorker<String,Void>() {
            @Override protected String doInBackground(){var result=session.apply();return result.paths()+" nehir uygulandı; "+result.changedCells()+" hücre. Tek adımda geri alınabilir.";}
            @Override protected void done(){
                busy=false;search.setEnabled(true);cancel.setText("Kapat");
                try{status.setText(get());}catch(Exception failure){status.setText("Uygulama tamamlanamadı: "+message(failure));}
                if(closeAfterWork)dispose();
            }
        }.execute();
    }
    static String statusText(RiverSearchResult result) {
        String state=switch(result.status()) {
            case FOUND->"İstenen nehirler bulundu.";
            case PARTIAL->"İstenen sayıdan daha az uygun nehir bulundu.";
            case BUDGET_EXHAUSTED->"Arama sınırına ulaşıldı; tüm alternatifler incelenemedi.";
            case NO_FEASIBLE_OUTLET->"İncelenen adaylarda güvenli çıkış bulunamadı.";
            case CANCELLED->"Arama iptal edildi. Dünya değiştirilmedi.";
            case STALE_WORLD->"Dünya değişti; yeniden arayın.";
        };
        return state+"\nBulunan: "+result.courses().size()+" / "+result.requested()
                +"; süre: "+result.diagnostics().elapsedMillis()/1000+" sn; özet: "+result.diagnostics().overviewStep()
                +" blok.\n"+(result.canApply()?"Önizlemeyi kontrol edip Uygula'ya basabilirsiniz. ":"")
                +(result.status()==RiverSearchResult.Status.FOUND?"":result.diagnostics().reason());
    }
    private static String message(Exception e){Throwable cause=e;while(cause.getCause()!=null)cause=cause.getCause();return String.valueOf(cause.getMessage());}

    static BufferedImage renderPreview(Dimension dimension,RiverSearchResult result) {
        int minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE;
        for(var cell:result.cells()){minX=Math.min(minX,cell.x());minY=Math.min(minY,cell.y());maxX=Math.max(maxX,cell.x());maxY=Math.max(maxY,cell.y());}
        BufferedImage image=new BufferedImage(640,480,BufferedImage.TYPE_INT_RGB);
        if(result.cells().isEmpty())return image;
        double scale=Math.min(600.0/Math.max(1,(long)maxX-minX+33),440.0/Math.max(1,(long)maxY-minY+33));
        double originX=minX-16,originY=minY-16;
        for(int py=0;py<480;py++)for(int px=0;px<640;px++) {
            int x=(int)Math.floor(originX+px/scale),y=(int)Math.floor(originY+py/scale);
            int colour=0x25282c;
            if(dimension.isTilePresent(x>>7,y>>7)) {
                float h=dimension.getHeightAt(x,y);
                int shade=Math.max(30,Math.min(170,(int)(h-dimension.getMinHeight())/3));
                colour=dimension.getWaterLevelAt(x,y)>Math.round(h)?0x275b96:(shade/2<<16)|(shade<<8)|shade/3;
            }
            image.setRGB(px,py,colour);
        }
        Graphics2D g=image.createGraphics();
        try {
            g.setColor(new Color(235,153,44));
            int size=Math.max(1,(int)Math.ceil(scale));
            for(var cell:result.cells())g.fillRect((int)((cell.x()-originX)*scale),(int)((cell.y()-originY)*scale),size,size);
            g.setColor(new Color(40,170,255));
            for(var course:result.courses())for(int i=1;i<course.centreline().size();i++) {
                var a=course.centreline().get(i-1);var b=course.centreline().get(i);
                g.drawLine((int)((a.x()-originX)*scale),(int)((a.y()-originY)*scale),(int)((b.x()-originX)*scale),(int)((b.y()-originY)*scale));
            }
        }finally{g.dispose();}return image;
    }
    private final App app;private final Dimension dimension;private final RiverSearchSession.Settings settings;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final JLabel preview=new JLabel();private final JTextArea status=new JTextArea(4,65);
    private final JProgressBar progress=new JProgressBar(0,100);
    private final JButton search=new JButton("Rota ara"),apply=new JButton("Uygula"),cancel=new JButton("Kapat");
    private RiverSearchSession session;private boolean busy,closeAfterWork;
}
