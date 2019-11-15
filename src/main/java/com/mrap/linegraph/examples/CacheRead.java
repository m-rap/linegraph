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
    
    class PpgWriter implements Consumer<Object[]> {
        int id;
        int ppgCount = 0;
        short[] ppgs = new short[25];
        long start = 0, end = 0;
        int counter = 0;
        
        FileOutputStream fwData = null, fwIndex = null;
        
        public PpgWriter(String prefix) throws IOException {
            fwData = new FileOutputStream(prefix + ".dt2");
            fwIndex = new FileOutputStream(prefix + ".ix2");
        }
        
        byte[] composePpg() {
            byte[] buff = new byte[1 + 1 + 4 + 4 + 1 + ppgs.length * 2];
            ByteBuffer buffer = ByteBuffer.wrap(buff);
            buffer.put((byte)0xAA);
            buffer.put((byte)((id-1) & 0xFF));
            buffer.putInt((int)(start & 0xFFFFFFFFL));
            buffer.putInt((int)(end & 0xFFFFFFFFL));
    //        System.out.println(counter);
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
            if (ppgCount == 0)
                start = (long)tmp[0];
            else if (ppgCount == 24) {
                end = (long)tmp[0];
                try {
                    fwData.write(composePpg());
                } catch (IOException ex) {
                    Logger.getLogger(CacheRead.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            ppgs[ppgCount] = (short)((float)tmp[0] * 10.0f);
            ppgCount++;
//            System.out.print((int)t[0] + " " + (long)t[1] + " " + (long)tmp[0]);
//            for (int i = 1; i < n; i++) {
//                System.out.print(" " + (float)tmp[i]);
//            }
//            System.out.println();
        }
    }
    
    HBox createField() {
        TextField t = new TextField();
        HBox.setHgrow(t, Priority.ALWAYS);
        HBox hbox = new HBox(t);
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
                ArrayList<File> files = new ArrayList<>();
                for (HBox hbox : fields) {
                    File f = new File(((TextField)hbox.getChildren().get(0)).getText());
                    if (f.exists() && f.isFile()) {
                        files.add(f);
                    }
                }
                ExecutorService es = Executors.newFixedThreadPool(files.size());
                ArrayList<Callable<Integer>> callables = new ArrayList<>();
                for (File f : files) {
                    callables.add(new Callable<Integer>() {
                        @Override
                        public Integer call() throws Exception {
                            try {
                                CacheableData.loadCacheData(f.getPath(), -1, -1, new Consumer<Object[]>() {
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
                                });
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
