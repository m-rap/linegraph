/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mrap.linegraph.examples;

import com.mrap.data.CacheableData;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 *
 * @author Rian
 */
public class CacheRead extends Application {
    
    final long DOTNET_BASE_TIME = 630822816000000000L; // 1 Januari 2000
    final long DOTNET_TICKS_PER_SECOND = 10000000L;
    
    long getDotNetTime(long ts)
    {
        Calendar c = Calendar.getInstance();
        c.set(2000, 0, 1); // 1 January 2000
        Date d = c.getTime();

        return (ts - d.getTime())/1000 * DOTNET_TICKS_PER_SECOND + DOTNET_BASE_TIME;
    }

    long fromDotNetTime(long t)
    {
        Calendar c = Calendar.getInstance();
        c.set(2000, 0, 1); // 1 January 2000
        Date d = c.getTime();

        return ((t - DOTNET_BASE_TIME) / DOTNET_TICKS_PER_SECOND)*1000 + d.getTime();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
    
    VBox vbox;
    ArrayList<HBox> fields = new ArrayList<>();
    
    Consumer<Object[]> defaultConsumer = new Consumer<Object[]>() {
        @Override
        public void accept(Object[] t) {
            Object[] tmp = (Object[])t[2];
            int n = tmp.length;
            System.out.print((int)t[0] + " " + (long)t[1] + " " + (long)tmp[0]);
            for (int i = 1; i < n; i++) {
                System.out.print(" " + (float)tmp[i]);
            }
            System.out.println();
        }
    };
    
    abstract class Dt2Writer implements Consumer<Object[]> {
        
        FileOutputStream fwData = null, fwIndex = null;
        
        public Dt2Writer(String prefix) throws IOException {
            fwData = new FileOutputStream(prefix + ".dt2");
            fwIndex = new FileOutputStream(prefix + ".ix2");
            ByteBuffer buff = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
            buff.putInt(1);
            fwIndex.write(buff.array());
        }
        
        public void close() throws IOException {
            if (fwData == null || fwIndex == null)
                return;
            fwData.flush();
            fwData.close();
            fwIndex.flush();
            fwIndex.close();
            fwData = null;
            fwIndex = null;
        }
    }
    
    class PpgWriter extends Dt2Writer {
        int id;
        int ppgCount = 0;
        short[] ppgs = new short[25];
        long start = 0, end = 0;
        int counter = 0;
        long dataStart = 0;
        long lastTs = 0;

        public PpgWriter(int id, String prefix) throws IOException {
            super(prefix);
            this.id = id;
        }
        
        byte[] composePpg() {
            byte[] buff = new byte[1 + 1 + 4 + 4 + 1 + ppgs.length * 2];
            ByteBuffer buffer = ByteBuffer.wrap(buff).order(ByteOrder.BIG_ENDIAN);
            buffer.put((byte)0xAA);
            buffer.put((byte)((id-1) & 0xFF));
            buffer.putInt((int)(start & 0xFFFFFFFFL));
            buffer.putInt((int)(end & 0xFFFFFFFFL));
            buffer.put((byte)(counter & 0xFF));
            counter = (counter + 1) % 6;
            for (int i = 0; i < ppgs.length; i++) {
                buffer.putShort(ppgs[i]);
            }
            return buff;
        }
        
        @Override
        public void accept(Object[] t) {
            if (fwData == null || fwIndex == null)
                return;
            
            Object[] tmp = (Object[])t[2];
            int n = tmp.length;
            if (n < 1)
                return;
            ppgs[ppgCount] = (short)((float)tmp[1] * 10.0f);
            long ts = (long)tmp[0];
            if (ts < lastTs) {
                try {
                    close();
                    return;
                } catch (IOException ex) {
                    Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            lastTs = ts;
            if (ppgCount == 0)
                start = ts;
            else if (ppgCount == 24) {
                end = ts;
                try {
                    byte[] data = composePpg();
                    fwData.write(data);
                    ByteBuffer indexBuff = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
                    indexBuff.putInt((int)(start & 0xFFFFFFFF));
                    indexBuff.putShort((short)(data.length & 0xFFFF));
                    fwIndex.write(indexBuff.array());
                    System.out.println("write ppg " + start + " " + end + " " + (end-start) + " " + data.length + " " + fwData.getChannel().position() + " " + (long)t[3]);
                } catch (IOException ex) {
                    Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            ppgCount = (ppgCount + 1) % 25;
        }
    }
    
    class HrWriter extends Dt2Writer {
        int id;
        long lastTs = 0;

        public HrWriter(int id, String prefix) throws IOException {
            super(prefix);
            this.id = id;
        }
        
        byte[] composeHr(int id, int spo2, int hr, int avg, long ts) {
            byte[] buff = new byte[1 + 1 + 4 + 3 * 2];
            ByteBuffer buffer = ByteBuffer.wrap(buff);
            buffer.put((byte)0xBB);
            buffer.put((byte)((id-1) & 0xFF));
            buffer.putInt((int)(ts & 0xFFFFFFFFL));
            buffer.putShort((short)spo2);
            buffer.putShort((short)hr);
            buffer.putShort((short)avg);
            return buff;
        }

        @Override
        public void accept(Object[] t) {
            if (fwData == null || fwIndex == null)
                return;
            
            Object[] tmp = (Object[])t[2];
            int n = tmp.length;
            if (n < 1)
                return;
            short[] datashort = new short[3];
            for (int i = 0; i < n-1; i++)
                datashort[i] = (short)((float)tmp[i + 1] * 10.0f);
            try {
                long ts = (long)tmp[0];
                if (ts < lastTs) {
                    try {
                        close();
                        return;
                    } catch (IOException ex) {
                        Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                lastTs = ts;
                byte[] data = composeHr(id, datashort[0], datashort[1], datashort[2], ts);
                fwData.write(data);
                ByteBuffer indexBuff = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
                indexBuff.putInt((int)(ts & 0xFFFFFFFF));
                indexBuff.putShort((short)(data.length & 0xFFFF));
                fwIndex.write(indexBuff.array());
                System.out.println("write hr " + ts + " " + data.length + " " + (long)t[3]);
            } catch (IOException ex) {
                Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
    }
    
    HBox createField() {
        TextField t = new TextField();
        TextField t2 = new TextField();
        TextField t3 = new TextField();
        TextField t4 = new TextField();
        HBox.setHgrow(t, Priority.ALWAYS);
        HBox hbox = new HBox(t, t2, t3, t4);
        hbox.setOnDragOver(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
        });
        hbox.setOnDragDropped(new EventHandler<DragEvent>() {
            @Override
            public void handle(DragEvent event) {
                Dragboard db = event.getDragboard();
                if (db.hasFiles())
                    t.setText(db.getFiles().get(0).getPath());
            }
        });
        Button removeBtn = new Button("-");
        removeBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                vbox.getChildren().remove(hbox);
                fields.remove(hbox);
            }
        });
        hbox.getChildren().add(removeBtn);
        return hbox;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Button addBtn = new Button("+");
        addBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                HBox hbox = createField();
                vbox.getChildren().add(hbox);
                fields.add(hbox);
            }
        });
        
        Button startBtn = new Button("Start");
        startBtn.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                ArrayList<Object[]> items = new ArrayList<>();
                for (HBox hbox : fields) {
                    File f = new File(((TextField)hbox.getChildren().get(0)).getText());
                    if (f.exists() && f.isFile()) {
                        Consumer<Object[]> consumer = defaultConsumer;
                        String type = ((TextField)hbox.getChildren().get(1)).getText();
                        try {
                            if (type.equals("ppg")) {
                                consumer = new PpgWriter(1, ((TextField)hbox.getChildren().get(2)).getText());
                            } else if (type.equals("hr")) {
                                consumer = new HrWriter(1, ((TextField)hbox.getChildren().get(2)).getText());
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        String jumpToByte = ((TextField)hbox.getChildren().get(3)).getText();
                        long jumpToByteL = -1;
                        try {
                            jumpToByteL = Long.parseLong(jumpToByte);
                        } catch (NumberFormatException ex) {}
                        items.add(new Object[]{f, consumer, jumpToByteL});
                    }
                }
                ExecutorService es = Executors.newFixedThreadPool(items.size());
                ArrayList<Callable<Integer>> callables = new ArrayList<>();
                for (Object[] objs : items) {
                    File f = (File)objs[0];
                    callables.add(new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            try {
                                Consumer<Object[]> cons = (Consumer<Object[]>)objs[1];
                                CacheableData.loadCacheData(f.getPath(), -1, -1, (long)objs[2], cons);
                                if (cons instanceof Dt2Writer) {
                                    ((Dt2Writer)cons).close();
                                    System.out.println("dt2 finished");
                                }
                            } catch (IOException ex) {
                                Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
                                return -1;
                            }
                            return 0;
                        }
                    });
                }
                try {
                    List<Future<Integer>> futures = es.invokeAll(callables);
                    for (Future<Integer> future : futures) {
                        try {
                            future.get();
                        } catch (ExecutionException ex) {
                            Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    es.shutdown();
                } catch (InterruptedException ex) {
                    Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
        
        HBox tools = new HBox(addBtn, startBtn);
        HBox hbox = createField();
        fields.add(hbox);
        vbox = new VBox(tools, hbox);
        vbox.setPrefHeight(400);
        vbox.setPrefWidth(500);
        
        Scene scene = new Scene(vbox);
        primaryStage.setTitle("Cache Read");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest((WindowEvent event) -> {
        });
        
        primaryStage.show();
    }
}
