package org.pepsoft.worldpainter.tools;

import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Version;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.tools.scripts.DrawnRiverGraph;
import org.pepsoft.worldpainter.tools.scripts.DrawnRiverNormalizer;
import org.pepsoft.worldpainter.tools.scripts.DrawnRiverSession;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Pencil mask is input only. Closing the preview never applies it. */
public final class DrawnRiverDialog extends WorldPainterDialog {
    DrawnRiverDialog(App app,Dimension dimension,Layer layer,RiverPreset preset,boolean smooth) {
        this(app,dimension,layer,preset,smooth,true);
    }
    DrawnRiverDialog(App app,Dimension dimension,Layer layer,RiverPreset preset,boolean smooth,boolean waterlineBankDetail) {
        this(app,dimension,layer,preset.sourceWidth,preset.maximumWidth,preset.depth,smooth,true,
                waterlineBankDetail,dimension.getSeed());
    }
    private DrawnRiverDialog(App app,Dimension dimension,Layer layer,double sourceWidth,double maximumWidth,
                             double depth,boolean smooth,boolean granite,boolean waterlineBankDetail,long seed) {
        super(app);
        setTitle("Çizimden nehir ağı — "+Version.VERSION+" ("+Version.BUILD+")");
        setModal(true);
        var status=new JTextArea(9,70);status.setEditable(false);status.setLineWrap(true);status.setWrapStyleWord(true);
        status.setText(DrawnRiverSession.identity()+"\n"
                +"Gri: çizim; mor: birleşim onarımı; cyan: önerilen hat; turuncu: kazı/dolgu; mavi: kanal; kahverengi: dirt −1; yeşil: üst kıyı.\n"
                +"Kalemler: Path (mavi) · Mini (yeşil, 1→kalın) · Outlet (kırmızı ağız) · Continue (turuncu gövde).\n"
                +"Nehri hazırla: önce araziyi korur (en fazla 120 sn), gerekirse kalan süreyle ortak vadi (16 blok ek kazı / 2 dolgu, toplam 5 dk).\n"
                +"Çıkış kalemi birden fazla ağız işaretleyebilir; yoksa otomatik/yön seçimi. Dünya yalnız Uygula ile değişir.");
        var picture=new PreviewPanel();picture.setPreferredSize(new java.awt.Dimension(700,460));
        var prepare=new JButton("Nehri hazırla");
        var clearExclude=new JButton("Kenar hariç tutmayı temizle");
        var pickOutlet=new JToggleButton("Çıkışı seç");
        var apply=new JButton("Uygula");var cancel=new JButton("Kapat");
        apply.setEnabled(false);
        var penPath=new JButton("Path");
        var penMini=new JButton("Mini");
        var penOutlet=new JButton("Outlet");
        var penContinue=new JButton("Continue");
        penPath.setToolTipText("Mavi ana hat kalemi");
        penMini.setToolTipText("Yeşil mini kaynak — 1 blok başlar");
        penOutlet.setToolTipText("Kırmızı çıkış ağzı — birden fazla işaretle");
        penContinue.setToolTipText("Turuncu devam/gövde — kenardan kalın giriş");
        penPath.addActionListener(e->{RiverPathSupport.ensureAllRiverDrawingLayers(app);app.selectRiverPathLayerForPainting();});
        penMini.addActionListener(e->{RiverPathSupport.ensureAllRiverDrawingLayers(app);app.selectRiverMiniLayerForPainting();});
        penOutlet.addActionListener(e->{RiverPathSupport.ensureAllRiverDrawingLayers(app);app.selectRiverOutletLayerForPainting();});
        penContinue.addActionListener(e->{RiverPathSupport.ensureAllRiverDrawingLayers(app);app.selectRiverContinueLayerForPainting();});
        var drainCombo=new JComboBox<>(new String[]{
                "Otomatik","Sol alt (SW)","Alt (S)","Sol (W)","Sağ alt (SE)","Üst (N)","Sağ (E)","Sağ üst (NE)","Sol üst (NW)"
        });
        drainCombo.setToolTipText("Ağ çıkış yönü tercihi (Outlet kalemi yoksa). Otomatik: pencerelenmiş kot + harita kenarı.");
        var drainRow=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));
        drainRow.add(new JLabel("Kalem:"));
        drainRow.add(penPath);drainRow.add(penMini);drainRow.add(penOutlet);drainRow.add(penContinue);
        drainRow.add(new JLabel("  Çıkış:"));
        drainRow.add(drainCombo);
        drainRow.add(pickOutlet);
        var controls=new JPanel();controls.add(prepare);controls.add(clearExclude);controls.add(apply);controls.add(cancel);
        getContentPane().setLayout(new BorderLayout());add(picture,BorderLayout.CENTER);
        var bottom=new JPanel(new BorderLayout());
        bottom.add(drainRow,BorderLayout.NORTH);
        bottom.add(new JScrollPane(status),BorderLayout.CENTER);
        bottom.add(controls,BorderLayout.SOUTH);
        add(bottom,BorderLayout.SOUTH);
        Runnable stop=()->{if(busy)cancelled.set(true);else dispose();};cancel.addActionListener(e->stop.run());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){stop.run();}});
        drainCombo.addActionListener(e->{
            if(busy)return;
            selectedDrain=drainFromCombo(drainCombo.getSelectedIndex());
            apply.setEnabled(false);
            status.setText("Çıkış yönü: "+drainCombo.getSelectedItem()+". Önceki sonuç geçersiz; yeniden Nehri hazırla.");
        });
        pickOutlet.addActionListener(e->{
            outletPickMode=pickOutlet.isSelected();
            edgePick=null;
            status.setText(outletPickMode
                    ? "Çıkış seçim modu: preview'de ağız pikseline tıklayın."
                    : "Kenar hariç tutma modu: iki bitişik noktaya tıklayın.");
        });
        prepare.addActionListener(e->runPrepare(app,dimension,layer,sourceWidth,maximumWidth,depth,smooth,granite,
                waterlineBankDetail,seed,status,picture,prepare,apply,cancel,clearExclude,drainCombo,pickOutlet));
        clearExclude.addActionListener(e->{
            if(busy)return;
            excludedEdges.clear();
            outletOverride=null;
            if(session!=null){
                session.setExcludedEdges(Set.of());
                session.setOutletOverride(null);
            }
            apply.setEnabled(false);
            status.setText("Kenar hariç tutma ve manuel çıkış temizlendi; önceki sonuç geçersiz. Yeniden Nehri hazırla.");
        });
        picture.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if(busy||picture.data==null)return;
                DrawnRiverGraph.Pixel hit=picture.pixelAt(e.getX(),e.getY());
                if(hit==null)return;
                if(outletPickMode){
                    outletOverride=hit;
                    if(session!=null)session.setOutletOverride(hit);
                    apply.setEnabled(false);
                    status.setText("Manuel çıkış: "+hit+". Önceki sonuç geçersiz; yeniden hazırlanıyor…");
                    runPrepare(app,dimension,layer,sourceWidth,maximumWidth,depth,smooth,granite,
                            waterlineBankDetail,seed,status,picture,prepare,apply,cancel,clearExclude,drainCombo,pickOutlet);
                    return;
                }
                if(edgePick==null){edgePick=hit;status.setText("Kenar hariç tutma: ilk nokta "+hit+". Bitişik ikinci noktayı seçin.");return;}
                DrawnRiverGraph.Edge edge=DrawnRiverGraph.Edge.of(edgePick,hit);
                edgePick=null;
                excludedEdges.add(edge);
                if(session!=null)session.setExcludedEdges(excludedEdges);
                apply.setEnabled(false);
                status.setText("Kenar hariç: "+edge.a()+"—"+edge.b()+". Önceki sonuç geçersiz; yeniden hazırlanıyor…");
                runPrepare(app,dimension,layer,sourceWidth,maximumWidth,depth,smooth,granite,
                        waterlineBankDetail,seed,status,picture,prepare,apply,cancel,clearExclude,drainCombo,pickOutlet);
            }
        });
        apply.addActionListener(e->{
            if(busy||session==null)return;
            if(app.getWorld()!=dimension.getWorld()||session.isStale()){apply.setEnabled(false);status.setText("Dünya, çizim, ayar veya hariç tutulan kenar değişti; yeniden hazırlayın.");return;}
            busy=true;cancelled.set(false);apply.setEnabled(false);prepare.setEnabled(false);clearExclude.setEnabled(false);
            drainCombo.setEnabled(false);pickOutlet.setEnabled(false);cancel.setText("İptal");
            new SwingWorker<String,Void>() {
                protected String doInBackground(){var r=session.apply();return r.paths()+" parça uygulandı. Tek adımda geri alınabilir.";}
                protected void done(){
                    busy=false;prepare.setEnabled(true);clearExclude.setEnabled(true);
                    drainCombo.setEnabled(true);pickOutlet.setEnabled(true);cancel.setText("Kapat");
                    try{status.setText(get());}catch(Exception failure){status.setText(explain(failure));}
                }
            }.execute();
        });
        pack();setLocationRelativeTo(app);
    }

    private static DrawnRiverGraph.Drain drainFromCombo(int index) {
        return switch (index) {
            case 1 -> DrawnRiverGraph.Drain.SW;
            case 2 -> DrawnRiverGraph.Drain.S;
            case 3 -> DrawnRiverGraph.Drain.W;
            case 4 -> DrawnRiverGraph.Drain.SE;
            case 5 -> DrawnRiverGraph.Drain.N;
            case 6 -> DrawnRiverGraph.Drain.E;
            case 7 -> DrawnRiverGraph.Drain.NE;
            case 8 -> DrawnRiverGraph.Drain.NW;
            default -> DrawnRiverGraph.Drain.AUTO;
        };
    }

    private void runPrepare(App app,Dimension dimension,Layer layer,double sourceWidth,double maximumWidth,
                            double depth,boolean smooth,boolean granite,boolean waterlineBankDetail,long seed,
                            JTextArea status,PreviewPanel picture,JButton prepare,JButton apply,JButton cancel,
                            JButton clearExclude,JComboBox<String> drainCombo,JToggleButton pickOutlet) {
        if(busy)return;
        if(app.getWorld()!=dimension.getWorld()){status.setText("Açık dünya değişti; pencereyi yeniden açın.");return;}
        busy=true;cancelled.set(false);prepare.setEnabled(false);clearExclude.setEnabled(false);apply.setEnabled(false);
        drainCombo.setEnabled(false);pickOutlet.setEnabled(false);cancel.setText("İptal");
        picture.data=null;picture.outletPixel=null;picture.repaint();
        status.setText(DrawnRiverSession.identity()+"\nNehri hazırla: arazi koruma sonra gerekirse ortak vadi (toplam en fazla 5 dk). Dünya değiştirilmiyor.");
        session=new DrawnRiverSession(dimension,layer,sourceWidth,maximumWidth,depth,smooth,granite,
                waterlineBankDetail,seed,cancelled::get);
        session.setExcludedEdges(excludedEdges);
        session.setDrain(selectedDrain);
        session.setOutletOverride(outletOverride);
        new SwingWorker<DrawnRiverSession.Preview,Void>() {
            protected DrawnRiverSession.Preview doInBackground(){return session.prepare();}
            protected void done(){
                busy=false;prepare.setEnabled(true);clearExclude.setEnabled(true);
                drainCombo.setEnabled(true);pickOutlet.setEnabled(true);cancel.setText("Kapat");
                try {
                    var result=get();picture.data=result;
                    picture.outletPixel=findOutletHint(result);
                    picture.repaint();
                    apply.setEnabled(result.canApply()&&!session.isStale()&&!cancelled.get());
                    status.setText(formatPreview(result));
                }catch(Exception failure){apply.setEnabled(false);status.setText(explain(failure));}
            }
        }.execute();
    }

    private static DrawnRiverGraph.Pixel findOutletHint(DrawnRiverSession.Preview result) {
        if(result==null||result.courses().isEmpty())return null;
        var c=result.courses().get(0).centreline();
        if(c.isEmpty())return null;
        var last=c.get(c.size()-1);
        return new DrawnRiverGraph.Pixel(last.x(),last.y());
    }

    static String formatPreview(DrawnRiverSession.Preview result) {
        StringBuilder sb=new StringBuilder();
        sb.append(result.identity()).append('\n');
        sb.append(uiLabel(result.uiClass())).append('\n');
        sb.append("Aşama: ").append(result.stageReached())
                .append(". Kaynak ").append(result.sources())
                .append(", birleşim ").append(result.junctions())
                .append(", parça ").append(result.courses().size())
                .append("; atlanan grup: ").append(result.skippedGroups())
                .append("; arazi reddi: ").append(result.terrainRejected())
                .append(". Azami ek kazı: ").append(String.format("%.2f", result.maxExtraCut())).append(".\n");
        if(!result.repairs().isEmpty())sb.append("Birleşim onarımı: ").append(result.repairs().size()).append(" hücre (geçici maske).\n");
        if(!result.blockers().isEmpty())sb.append("Engeller: ").append(String.join(" | ",result.blockers())).append("\n");
        sb.append(String.join("\n",result.messages()));
        return sb.toString();
    }

    private static String uiLabel(DrawnRiverSession.UiClass c) {
        return switch (c) {
            case UNRESOLVED_DRAWING -> "Çizim çözümlenemedi";
            case TERRAIN_UNSUITABLE -> "Ağ çözüldü, arazi uygun değil";
            case AMBIGUOUS_OUTLET -> "Çıkış belirsiz";
            case OK -> "Ağ çözüldü";
        };
    }

    public static void queueScriptPreview(App app,Dimension dimension,Layer layer,double sourceWidth,double maximumWidth,
                                           double depth,boolean smooth,boolean granite,long seed) {
        SwingUtilities.invokeLater(()->{
            long requestedAt=System.nanoTime();
            Timer timer=new Timer(100,null);
            timer.addActionListener(e->{
                if(app.getWorld()!=dimension.getWorld()||System.nanoTime()-requestedAt>30_000_000_000L){timer.stop();return;}
                if(dimension.isEventsInhibited())return;
                timer.stop();new DrawnRiverDialog(app,dimension,layer,sourceWidth,maximumWidth,depth,smooth,granite,true,seed).setVisible(true);
            });timer.start();
        });
    }
    static String explain(Exception e){
        Throwable t=e;while(t.getCause()!=null)t=t.getCause();
        String m=String.valueOf(t.getMessage());
        if(t instanceof CancellationException) {
            if(m.contains("bütçe"))return "Süre doldu: "+m;
            return "İptal: "+m;
        }
        return "Hesap hatası: "+m+" Dünya değiştirilmedi.";
    }
    private static final class PreviewPanel extends JPanel {
        DrawnRiverSession.Preview data;
        DrawnRiverGraph.Pixel outletPixel;
        int minX,minY,maxX,maxY;double scale=1;
        DrawnRiverGraph.Pixel pixelAt(int mx,int my){
            if(data==null||data.drawing().isEmpty()||scale<=0)return null;
            int x=(int)Math.floor(minX+(mx-20)/scale);
            int y=(int)Math.floor(minY+(my-20)/scale);
            DrawnRiverGraph.Pixel p=new DrawnRiverGraph.Pixel(x,y);
            if(data.drawing().contains(p)||data.repairs().contains(p))return p;
            for(var d:data.diagnostics())if(d.highlight().contains(p))return p;
            return null;
        }
        private int sx(int x){return 20+(int)((x-(double)minX)*scale);}
        private int sy(int y){return 20+(int)((y-(double)minY)*scale);}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);if(data==null||data.drawing().isEmpty())return;
            minX=Integer.MAX_VALUE;minY=Integer.MAX_VALUE;maxX=Integer.MIN_VALUE;maxY=Integer.MIN_VALUE;
            for(var p:data.drawing()){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());}
            for(var p:data.repairs()){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());}
            for(var p:data.suggestedCentreline()){minX=Math.min(minX,p.x());minY=Math.min(minY,p.y());maxX=Math.max(maxX,p.x());maxY=Math.max(maxY,p.y());}
            scale=Math.min((getWidth()-40.0)/Math.max(1,(long)maxX-minX),(getHeight()-40.0)/Math.max(1,(long)maxY-minY));
            Graphics2D g=(Graphics2D)graphics.create();
            try {
                g.setColor(Color.GRAY);for(var p:data.drawing())g.fillRect(sx(p.x()),sy(p.y()),2,2);
                g.setColor(new Color(150,60,180));
                int size=Math.max(1,(int)Math.ceil(scale));
                for(var p:data.repairs())
                    g.fillRect(sx(p.x()),sy(p.y()),Math.max(2,size),Math.max(2,size));
                for(var d:data.diagnostics()) {
                    if(d.kind()!=DrawnRiverNormalizer.IssueKind.REAL_LOOP)continue;
                    g.setColor(new Color(180,40,160));
                    for(var p:d.highlight())
                        g.fillRect(sx(p.x()),sy(p.y()),Math.max(2,size),Math.max(2,size));
                }
                g.setColor(new Color(220,140,40));
                for(var p:data.cutFillCells())
                    g.fillRect(sx(p.x()),sy(p.y()),size,size);
                g.setColor(new Color(0,180,200));
                for(var p:data.suggestedCentreline())
                    g.fillRect(sx(p.x()),sy(p.y()),Math.max(2,size),Math.max(2,size));
                g.setColor(new Color(35,145,235));
                for(var p:data.cells()) {
                    if(p.loweredDirt()||p.waterlineBank()) continue;
                    g.fillRect(sx(p.x()),sy(p.y()),size,size);
                }
                g.setColor(new Color(180,90,30));
                for(var p:data.cells()) {
                    if(!p.loweredDirt()||p.waterlineBank()) continue;
                    g.fillRect(sx(p.x()),sy(p.y()),size,size);
                }
                g.setColor(new Color(60,160,90));
                for(var p:data.cells()) {
                    if(!p.waterlineBank()) continue;
                    g.fillRect(sx(p.x()),sy(p.y()),size,size);
                }
                g.setColor(Color.WHITE);
                for(var c:data.courses()) {
                    var line=c.centreline();
                    if(line.size()<2)continue;
                    var mid=line.get(line.size()/2);
                    var next=line.get(Math.min(line.size()-1,line.size()/2+Math.max(1,line.size()/8)));
                    int x1=sx(mid.x()),y1=sy(mid.y()),x2=sx(next.x()),y2=sy(next.y());
                    g.drawLine(x1,y1,x2,y2);
                    double ang=Math.atan2(y2-y1,x2-x1);
                    int ax=(int)(x2-6*Math.cos(ang-0.5)),ay=(int)(y2-6*Math.sin(ang-0.5));
                    int bx=(int)(x2-6*Math.cos(ang+0.5)),by=(int)(y2-6*Math.sin(ang+0.5));
                    g.drawLine(x2,y2,ax,ay);g.drawLine(x2,y2,bx,by);
                    g.drawString(Math.round(c.startWidth())+" blok",x1+4,y1-2);
                }
                if(outletPixel!=null){
                    g.setColor(Color.RED);
                    int ox=sx(outletPixel.x()),oy=sy(outletPixel.y());
                    g.drawOval(ox-4,oy-4,10,10);
                    g.drawString("çıkış",ox+8,oy);
                }
            }finally{g.dispose();}
        }
    }
    private DrawnRiverSession session;private boolean busy;private final AtomicBoolean cancelled=new AtomicBoolean();
    private final Set<DrawnRiverGraph.Edge> excludedEdges=new HashSet<>();
    private DrawnRiverGraph.Pixel edgePick;
    private DrawnRiverGraph.Pixel outletOverride;
    private DrawnRiverGraph.Drain selectedDrain=DrawnRiverGraph.Drain.AUTO;
    private boolean outletPickMode;
}
