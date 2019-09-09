/*
 * The MIT License
 *
 * Copyright 2019 Your Organisation.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mrap.linegraph;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;

/**
 *
 * @author software
 */
public class LineGraph extends GridPane {

    public class Field {
        public CheckBox cbData;
        public Label txtData;
        public Paint color;
    }

    static final int CLEAR_DATA_MS = 5 * 60 * 1000; // 5 menit
    private long DUMP_INTERVAL_MS = 60 * 1000;
    static float MIN_AUTOTICK = 0.005f;
    final static float DEFAULT_TICK_COUNT = 5.0f;
    private static final String[] DEFAULT_NAMES = {"X", "Y", "Z"};

    @FXML
    GridPane gridPane;
    @FXML
    ScrollPane pane;
    @FXML
    Canvas canvas;
    @FXML
    HBox scaleBox; // 1,0
    @FXML
    AnchorPane xRuler; // 1,1
    @FXML
    AnchorPane yRuler; // 0,2
    @FXML
    AnchorPane scrollBar; // 1,3
    @FXML
    HBox legendBox;
    @FXML
    HBox legendBoxBase; // 1,4
    
    @FXML
    CheckBox cbAutoscale;
    
    private final Object dataLock = new Object();
    
    private int nFields = -1;
    private Field[] fields;
    
    float dataYMin = Float.MAX_VALUE, dataYMax = Float.MIN_VALUE, autoYUnitTick = 1;
    private int currIdx = 0;
    protected long startMs = -1;
    int dumpedSize = 0;
    boolean isDumping = false;
    private int dumpedEndIdx = -1;
    private int dumpStartIdx = 0;
    private int dumpEndIdx = -1;
    private boolean isSaving = false;
    boolean scrollBarLocked = false;
    long showStart = -1;
    private float autoTickCount = DEFAULT_TICK_COUNT;

    Rectangle scrollRect;
    private float yMin;
    private float yMax;
    private float yUnitTick;
    private float xUnitTick;
    private double widthPerSec;
    private double widthPerMs;
    
    float autoYMin, autoYMax;
    private ArrayList<Object[]> data;
    GraphicsContext gc;
    private double lineWidth;
    
    private float END_GAP = 0.9f;
    private long totalShowMs;
    private long totalMs;
    final private Object lockScrollBar = new Object();
    private int prevMouseX;
    private int prevMouseY;
    private boolean showScaleBox = true;
    private boolean showXRuler = true;
    private boolean showYRuler = true;
    private boolean showScrollBar = true;
    private boolean showLegendBox = true;
    
    private final Object fileDumpLock = new Object();
    protected String name = "";
    File dir = new File(".linegraph");
    String debugStr = "";
    
    AnimationTimer fxTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (!isVisible() || getParent() == null)
                return;
            display();
        }
    };
    
    static ThreadFactory dumpThreadFactory = (Runnable r) -> new Thread(r, "dumper");
    static ThreadFactory terminatorThreadFactory = (Runnable r) -> new Thread(r, "terminator");
    
    private ScheduledExecutorService dumpExecutor = Executors.newSingleThreadScheduledExecutor(dumpThreadFactory);
    private ScheduledExecutorService executorTerminator = Executors.newSingleThreadScheduledExecutor(terminatorThreadFactory);
    private ScheduledFuture<?> dumpScheduledFuture = null;
    private ScheduledFuture<?> terminatorSceduledFuture = null;

    private Runnable dumpRunnable = new Runnable() {
        @Override
        public void run() {
            if (!enableDump)
                return;
            if (getData().isEmpty()) {
                return;
            }
            if (dumpStartIdx >= getData().size() || dumpStartIdx < 0) {
                return;
            }
            
            isDumping = true;
            
            ArrayDeque<Object[]> temp;
            synchronized (getData()) {
                long a = (long)getData().get(0)[0], b = (long)getData().get(getData().size() - 1)[0];
                log("start dumping from " + (long)getData().get(dumpStartIdx)[0] + 
                        ", current data size,start,end,length " + getData().size() + "," + a +
                        "," + b + "," + (b - a));
                dumpEndIdx = getData().size() - 1;
                temp = new ArrayDeque<>();
                for (; dumpStartIdx <= dumpEndIdx; dumpStartIdx++) {
                    temp.add(getData().get(dumpStartIdx));
                }
            }

            if (!temp.isEmpty()) {
                synchronized (fileDumpLock) {
                    try {
                        if (!dir.exists()) {
                            Files.createDirectory(dir.toPath());
                        }

                        File file = new File(getCachePath());
                        boolean firstCreate = !file.exists();
                        FileOutputStream fos = new FileOutputStream(file, true);
                        ByteBuffer buff = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                        if (firstCreate) {
                            buff.putInt(nFields);
                            fos.write(buff.array());
                        }
                        buff = ByteBuffer.allocate(8 + 4 * nFields).order(ByteOrder.LITTLE_ENDIAN);
                        for (Object[] datum : temp) {
                            buff.position(0);
                            buff.putLong((long) datum[0]);
                            for (int i = 0; i < nFields; i++)
                                buff.putFloat((float) datum[i + 1]);
                            fos.write(buff.array());
                        }
                        fos.flush();
                        fos.close();
                        dumpedSize += temp.size();
                        dumpedEndIdx = dumpEndIdx;

                        log(dumpedSize + " dumped, " +
                            ((float) ((long) temp.getLast()[0] - dumpStartIdx) / 60000) +
                            " mins length");

                    } catch (IOException ex) {
                        Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            isDumping = false;
        }
    };
    private SimpleDateFormat sdf;
    private boolean enableDump = true;

    public String getCachePath() {
        return getCachePathNoExt() + ".bin";
    }
    
    public String getCachePathNoExt() {
        return ".linegraph/" + getFinalName() + "_" + sdf.format(new Date(startMs));
    }

    public LineGraph getThis() {
        return this;
    }

    public LineGraph() {
        this(-5, 5, 1, 1, 20);
    }

    public LineGraph(float yMin1, float yMax1, float yUnitTick1, float xUnitTick1, float widthPerSec1) {
        this(yMin1, yMax1, yUnitTick1, xUnitTick1, widthPerSec1, new ArrayList<Object[]>());
    }
    
    public LineGraph(float yMin1, float yMax1, float yUnitTick1, float xUnitTick1, float widthPerSec1, ArrayList<Object[]> data1) {
        try {
            FXMLLoader loader = new FXMLLoader(LineGraph.class.getResource("/fxml/LineGraph.fxml"));
            loader.setRoot(this);
            loader.setController(this);
            loader.load();
        } catch (IOException ex) {
            Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
        }

        data = data1;
        autoTickCount = DEFAULT_TICK_COUNT;
        sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        
        setnFields(3);
        setyMin(yMin1);
        setyMax(yMax1);
        setyUnitTick(yUnitTick1);
        setxUnitTick(xUnitTick1);
        setWidthPerSec(widthPerSec1);
        
        scrollRect = (Rectangle) scrollBar.getChildren().get(0);
        gc = canvas.getGraphicsContext2D();
        
        //cbAutoscale.selectedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
        //    
        //    if (name != null && !name.isEmpty())
        //        PropertiesLoader.getDefault().setProperty("linegraph_" + name + "_autoscale", newValue ? "true" : "false");
        //});

        pane.heightProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            double height1 = newValue.doubleValue();
            if (height1 < 0) {
                height1 = 0;
            }
            onResize(canvas.getWidth(), height1);
        });

        pane.widthProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            double width1 = newValue.doubleValue();
            if (width1 < 0) {
                width1 = 0;
            }
            onResize(width1, canvas.getHeight());
        });
        
        fxTimer.start();
    }
    
    public void setScale(float yMin1, float yMax1, float yUnitTick1, float xUnitTick1, float widthPerSec1) {
        setyMin(yMin1);
        setyMax(yMax1);
        setyUnitTick(yUnitTick1);
        setxUnitTick(xUnitTick1);
        setWidthPerSec(widthPerSec1);
    }
    
    public void start() {
        fxTimer.start();
    }
    
    public void stop() {
        fxTimer.stop();
    }
    
    //public void setIsDrawChartOnSave(boolean isDrawChartOnSave) {
    //    this.isDrawChartOnSave = isDrawChartOnSave;
    //}

    public void setLegends(String[] names) {
        if (fields == null)
            return;
        
        for (int i = 0; i < fields.length; i++) {
            fields[i].txtData.setText(names[i]);
        }
    }

    private void setDefaultStyle() {
        Pane paneAux = new Pane();
        Scene sceneAux = new Scene(paneAux);
        
        if (fields == null)
            return;
        
        for (int i = 0; i < fields.length; i++) {
            Line line = new Line();
            line.getStyleClass().add("default-color" + (i%8));
            line.getStyleClass().add("chart-series-line");
            paneAux.getChildren().clear();
            paneAux.getChildren().add(line);
            line.applyCss();
            lineWidth = line.getStrokeWidth();
            fields[i].color = line.getStroke();
        }
    }

    private void onResize(double width, double height) {
        canvas.setWidth(width);
        canvas.setHeight(height);
    }
    
    ArrayDeque<Label> lbls = new ArrayDeque<>();
    final static Font AXIS_FONT = new Font("monospaced", 10);
    final static DecimalFormat DF = new DecimalFormat("#.###");

    private void drawAxisY() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        
        for (Node n : yRuler.getChildren())
            if (n instanceof Label && !lbls.contains((Label)n))
                lbls.add((Label)n);
        yRuler.getChildren().clear();
        gc.setLineWidth(0.5);
        gc.setStroke(Paint.valueOf("#989898"));
        float tmpYMin, tmpYMax, tmpYUnitTick;
        if (cbAutoscale.isSelected() && dataYMin < Float.MAX_VALUE) {
            tmpYMin = autoYMin;
            tmpYMax = autoYMax;
            tmpYUnitTick = autoYUnitTick;
        } else {
            tmpYMin = yMin;
            tmpYMax = yMax;
            tmpYUnitTick = yUnitTick;
        }
        float y = (int) (tmpYMin / tmpYUnitTick) * tmpYUnitTick + tmpYUnitTick;
        for (; y <= tmpYMax; y += tmpYUnitTick) {
            float actualY = tmpYMax - y;
            double scaledActualY = actualY * height / (tmpYMax - tmpYMin);
            if (gridPane.getChildren().contains(yRuler)) {
                Label text;
                if (!lbls.isEmpty())
                    text = lbls.pop();
                else
                    text = new Label();
                if (tmpYUnitTick < 1.0f)
                    text.setText(DF.format(y));
                else
                    text.setText(String.format("%.0f", y));
                text.setFont(AXIS_FONT);
                text.applyCss();
                if (scaledActualY - 5 + text.getBoundsInLocal().getHeight() < yRuler.getBoundsInLocal().getHeight() &&
                        scaledActualY - 5 > 0) {
                    AnchorPane.setBottomAnchor(text, null);
                    AnchorPane.setLeftAnchor(text, null);
                    AnchorPane.setTopAnchor(text, scaledActualY - 5);
                    AnchorPane.setRightAnchor(text, 0.0);
                    yRuler.getChildren().add(text);
                }
            }
            gc.strokeLine(0, scaledActualY, width, scaledActualY);
        }
    }

    private void drawAxisX(long showStart, long showEnd) {
        for (Node n : xRuler.getChildren())
            if (n instanceof Label && !lbls.contains((Label)n))
                lbls.add((Label)n);
        xRuler.getChildren().clear();
        long x = (long) ((float) Math.ceil(showStart / (xUnitTick * 1000)) * xUnitTick * 1000);
        double y2 = canvas.getHeight();
        while (x < showEnd) {
            double scaledActualX = (x - showStart) * widthPerMs;
            gc.strokeLine(scaledActualX, 0, scaledActualX, y2);
            if (gridPane.getChildren().contains(xRuler)) {
                Label text;
                if (!lbls.isEmpty())
                    text = lbls.pop();
                else
                    text = new Label();
                if (startMs == -1) {
                    text.setText("" + (x / 1000));
                } else {
                    text.setText("" + ((x + ((long) data.get(0)[0] - startMs)) / 1000));
                }
                text.setFont(AXIS_FONT);
                text.applyCss();
                if (scaledActualX + text.getBoundsInLocal().getWidth() < xRuler.getWidth() &&
                        scaledActualX > 0) {
                    AnchorPane.setRightAnchor(text, null);
                    AnchorPane.setBottomAnchor(text, null);
                    AnchorPane.setTopAnchor(text, 0.0);
                    AnchorPane.setLeftAnchor(text, scaledActualX);
                    xRuler.getChildren().add(text);
                } else {
                    xRuler.getChildren().remove(text);
                }
            }
            x += (xUnitTick * 1000);
        }
    }
    
    public void setData(ArrayList<Object[]> data) {
        resetData();
        synchronized (dataLock) {
            this.data = data;
            for (Object[] datum : data) {
                addDataInternal(datum, true);
            }
        }
    }

    public void addData(Object... datum) {
        if (isSaving) {
            return;
        }
        
        synchronized (dataLock) {
            addDataInternal(datum);

            long timeLength, a = 0, b = 0;
            try {
                a = (long)data.get(0)[0];
                timeLength = (long) datum[0] - a;
                b = enableDump ? (long)data.get(dumpStartIdx)[0] : 0;
                if (timeLength >= CLEAR_DATA_MS * 2 && (!enableDump || timeLength / 2 < b - a)) {
                    int dataToClearCount = data.size() / 2;
                    data.subList(0, dataToClearCount).clear();

                    if (enableDump)
                        dumpStartIdx -= dataToClearCount;

                    b = (long)data.get(data.size() - 1)[0];
                    log("old data dropped, current size,start,end,length " + data.size() + "," + a +
                        "," + b + "," + (b - a));
                }
            } catch (Exception e) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
                PrintWriter pw = new PrintWriter(bos);
                e.printStackTrace(pw);
                log(bos.toString() + "\n" + a + " " + b + " " + dumpStartIdx);
            }
            
            if (dumpScheduledFuture == null || dumpScheduledFuture.isDone()) {
                dumpScheduledFuture = dumpExecutor.scheduleAtFixedRate(dumpRunnable,
                        0, DUMP_INTERVAL_MS, TimeUnit.MILLISECONDS);
            }
            
            if (terminatorSceduledFuture != null && !terminatorSceduledFuture.isDone()) {
                terminatorSceduledFuture.cancel(true);
            }
            
            terminatorSceduledFuture = executorTerminator.schedule(() -> {
                dumpExecutor.shutdown();
                try {
                    dumpExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                }
                dumpExecutor = Executors.newSingleThreadScheduledExecutor(dumpThreadFactory);
                dumpRunnable.run();
                executorTerminator.shutdown();
                executorTerminator = Executors.newSingleThreadScheduledExecutor(terminatorThreadFactory);
            }, 1700, TimeUnit.MILLISECONDS);
        }
    }
    
    private void addDataInternal(Object[] datum) {
        addDataInternal(datum, false);
    }

    private void addDataInternal(Object[] datum, boolean simulate) {
        if (startMs == -1) {
            startMs = (long) datum[0];
        }
        
        if (!simulate)
            data.add(datum);
        boolean autoscaleChanged = false;
        
        for (int i = 0; i < nFields; i++) {
            if ((float) datum[i + 1] < dataYMin) {
                dataYMin = (float) datum[i + 1];
                autoscaleChanged = true;
            }
            if ((float) datum[i + 1] > dataYMax) {
                dataYMax = (float) datum[i + 1];
                autoscaleChanged = true;
            }
        }
        
        if (autoscaleChanged) {
            autoYUnitTick = dataYMax - dataYMin;
            if (autoYUnitTick <= MIN_AUTOTICK / autoTickCount)
                autoYUnitTick = MIN_AUTOTICK;
            autoYUnitTick /= autoTickCount;
            if (autoYUnitTick < 1.0f) {
                float scale = 1.0f / autoYUnitTick;
                autoYUnitTick = Math.round(autoYUnitTick * scale) / scale;
                autoYMin = dataYMin - autoYUnitTick;
                autoYMin = (float)Math.floor(autoYMin * scale) / scale;
                autoYMax = dataYMax + autoYUnitTick;
                autoYMax = (float)Math.ceil(autoYMax * scale) / scale;
            } else {
                autoYUnitTick = Math.round(autoYUnitTick);
                autoYMin = (float)Math.floor(dataYMin - autoYUnitTick);
                autoYMax = (float)Math.ceil(dataYMax + autoYUnitTick);
            }
        }
    }

    public void log(String msg) {
        try {
            File dumpLogFile = new File(getCachePathNoExt() + ".log");
            if (!dir.exists()) {
                Files.createDirectory(dir.toPath());
            }
            
            FileOutputStream dumpLogFos = new FileOutputStream(dumpLogFile, true);
            PrintWriter pw = new PrintWriter(dumpLogFos);
            
            pw.println(msg);
            pw.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void saveData(final long from, final long to, final Consumer<Object[]> dataConsumer,
            final Consumer<Object> progressConsumer, final String path, final String... headers) {
        Thread t = new Thread(() -> {
            fxTimer.stop();
            isSaving = true;
            try {
                File f = new File(path);
                if (f.exists()) {
                    throw new Exception("Saving failed: File exists");
                }
                FileWriter fw = new FileWriter(f);
                if (headers != null) {
                    for (int index = 0; index < headers.length; index++) {
                        if (index != 0) {
                            fw.write(",");
                        }
                        fw.write(headers[index]);
                    }
                    fw.write("\r\n");
                }
                
                ArrayList<Object[]> tempData = new ArrayList<>();
                long total;
                int counter = 0;
                synchronized (dataLock) {
                    tempData.addAll(data.subList(dumpedEndIdx + 1, data.size()));
                }
                synchronized (fileDumpLock) {
                    total = tempData.size();
                    ArrayList<Path> list = new ArrayList<>();
                    File cacheFile = new File(getCachePath());
                    if (cacheFile.exists() && cacheFile.canRead()) {
                        final FileWriter fw1 = fw;
                        final int counter0 = counter;
                        final long total0 = total;
                        long cacheCount = loadCacheData(from, to, (Object[] tmp) -> {
                            try {
                                int counter1 = (int)tmp[0];
                                long total1 = (long)tmp[1];
                                Object[] datum = (Object[])tmp[2];

                                long timestamp = (long)datum[0];
                                fw1.write((long)datum[0] + ",");
                                for (int i = 1; i < datum.length; i++)
                                    fw1.write((float)datum[i] + ",");

                                if (dataConsumer != null)
                                    dataConsumer.accept(datum);

                                if (progressConsumer != null)
                                    progressConsumer.accept((double) (counter1 + counter0) / (total0 + total1));

                            } catch (IOException ex) {
                                Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });
                        total += cacheCount;
                        counter += cacheCount;
                    }
                }
                
                int i = 0;
                for (Object[] row : tempData) {
                    long timestamp = (long) row[0];
                    if (to != -1 && timestamp > to) {
                        total -= (tempData.size() - i);
                        break;
                    }
                    if (from != -1 && timestamp < from) {
                        total--;
                        continue;
                    }
                    
                    fw.write(row[0].toString() + ",");
                    for (int j = 1; j < row.length; j++)
                        fw.write(row[j].toString() + ",");
                    
                    if (dataConsumer != null) {
                        dataConsumer.accept(row);
                    }
                    
                    counter++;
                    if (total <= 0)
                        total = 1;
                    if (progressConsumer != null && counter < total) {
                        progressConsumer.accept((double) counter / total);
                    }
                    i++;
                }
                fw.close();
                
                if (progressConsumer != null) {
                    progressConsumer.accept((double) 1);
                }
            } catch (Exception ex) {
                Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                if (progressConsumer != null) {
                    progressConsumer.accept(ex);
                }
            }
            isSaving = false;
            fxTimer.start();
        });
        t.start();
    }

    public void resetData() {
        while (isDumping || isSaving) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        synchronized (dataLock) {
            data.clear();
            startMs = -1;
            dataYMin = Float.MAX_VALUE;
            dataYMax = Float.MIN_VALUE;
            autoYUnitTick = 1;
            currIdx = 0;
            dumpedSize = 0;
            dumpedEndIdx = -1;
            dumpStartIdx = 0;
            dumpEndIdx = -1;
            scrollBarLocked = false;
            showStart = -1;
            autoTickCount = DEFAULT_TICK_COUNT;
        }
    }

    @FXML
    void scrollBar_onMousePressed(MouseEvent e) {
        prevMouseX = (int) e.getX();
        prevMouseY = (int) e.getY();
    }

    @FXML
    void scrollBar_onMouseReleased(MouseEvent e) {
        synchronized (lockScrollBar) {
            scrollBarLocked = false;
        }
        prevMouseX = -1;
        prevMouseY = -1;
    }

    @FXML
    void scrollBar_onMouseDrag(MouseEvent e) {
        synchronized (lockScrollBar) {
            scrollBarLocked = true;

            int curX = (int) e.getX();
            int curY = (int) e.getY();
            int deltaX = (int) e.getX() - prevMouseX;
            int deltaY = (int) e.getY() - prevMouseY;
            prevMouseX = curX;
            prevMouseY = curY;

            int x = (int) (scrollRect.getTranslateX() + deltaX);
            int xMax = (int) (scrollBar.getWidth() - scrollRect.getWidth());
            if (x > xMax) {
                x = xMax;
            }
            if (x < 0) {
                x = 0;
            }

            scrollRect.setTranslateX(x);
            showStart = (x >= xMax) ? -1 : (long) (x * totalMs / scrollBar.getWidth());
        }
    }
    
    ArrayDeque<float[]> createToDraw(long[] showStartEnd) {
        ArrayDeque<float[]> toDraw = new ArrayDeque<>();
        long showStart0, start, end, showEnd;
        synchronized (dataLock) {
            showStart0 = showStart;
            start = (!data.isEmpty()) ? (long) data.get(0)[0] : 0;
            end = (!data.isEmpty()) ? (long) data.get(data.size() - 1)[0] : 0;
            totalShowMs = (long) (canvas.getWidth() * 1000 / widthPerSec);
            totalMs = end - start + (long) ((float) totalShowMs * (1 - END_GAP));
            if (showStart == -1) {
                showStart0 = (totalMs > totalShowMs)
                        ? totalMs - totalShowMs
                        : 0;
            }
            float startPx = (float) Math.floor(showStart0 * widthPerMs);

            showEnd = showStart0 + totalShowMs;
            showStartEnd[0] = showStart0;
            showStartEnd[1] = showEnd;
            
            if (data.isEmpty())
                return toDraw;
            
            //debugStr = new StringBuilder().append(this.showStart).append(" ").
            //        append(showStart0).append(" ").append(showEnd).toString();
            
            int prevX = Integer.MIN_VALUE;
            long ts;
            
            float tmpYMin, tmpYMax;
            if (cbAutoscale.isSelected() && dataYMin < Float.MAX_VALUE) {
                tmpYMin = autoYMin;
                tmpYMax = autoYMax;
            } else {
                tmpYMin = yMin;
                tmpYMax = yMax;
            }
            
            for (; currIdx >= 0; currIdx--) {
                Object[] datum = data.get(currIdx);
                ts = (long) datum[0] - start;
                if (ts < showStart0)
                    break;
            }
            if (currIdx < 0) currIdx = 0;
            
            for (; currIdx < data.size(); currIdx++) {
                Object[] datum = data.get(currIdx);
                ts = (long) datum[0] - start;
                if (ts >= showStart0) {
                    break;
                }
            }
            if (currIdx > 0) currIdx--;
            
            for (int tempCurrIdx = currIdx; tempCurrIdx < data.size(); tempCurrIdx++) {
                Object[] datum = data.get(tempCurrIdx);
                ts = (long) datum[0] - start;
                float scaledX = (float) Math.floor(ts * widthPerMs);
                
                if ((int) scaledX == prevX) {
                    continue;
                }
                prevX = (int) scaledX;
                    
                float[] toDrawEl = new float[nFields + 1];
                toDrawEl[0] = scaledX - startPx;
                for (int i = 0; i < datum.length - 1; i++) {
                    if (!fields[i].cbData.isSelected()) {
                        continue;
                    }
                    float scaledY = -((float) datum[i + 1] - tmpYMax) *
                            (float) canvas.getHeight() / (tmpYMax - tmpYMin);
                    toDrawEl[i + 1] = scaledY;
                }
                toDraw.add(toDrawEl);
                
                if (ts > showEnd) {
                    break;
                }
            }
        }
        
        return toDraw;
    }

    void display() {
        long[] showStartEnd = new long[2];
        ArrayDeque<float[]> toDraw = createToDraw(showStartEnd);
        long showStart0 = showStartEnd[0], showEnd = showStartEnd[1];
        
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        drawAxisY();

        synchronized (lockScrollBar) {
            if (!scrollBarLocked) {
                double scrollBarWidth = canvas.getWidth();
                if (totalMs > totalShowMs) {
                    double width = (double) (totalShowMs * scrollBarWidth) / totalMs;
                    double translateX = (double) (showStart0 * scrollBarWidth) / totalMs;
                    if (translateX > scrollBarWidth - width) {
                        translateX = scrollBarWidth - width;
                    }
                    scrollRect.setWidth(width);
                    scrollRect.setTranslateX(translateX);
                } else {
                    scrollRect.setWidth(scrollBarWidth);
                    scrollRect.setTranslateX(0.0);
                }
                scrollBar.setMinWidth(scrollBarWidth);
                scrollBar.setPrefWidth(scrollBarWidth);
                scrollBar.setMaxWidth(scrollBarWidth);
            }
        }

        drawAxisX(showStart0, showEnd);

        gc.setLineWidth(lineWidth);
        for (int i = 0; i < nFields; i++) {
            if (!fields[i].cbData.isSelected()) {
                continue;
            }

            gc.setStroke(fields[i].color);
            Iterator<float[]> it = toDraw.iterator();
            if (!it.hasNext()) {
                break;
            }
            float[] toDrawEl = it.next();
            gc.beginPath();
            gc.moveTo((double) toDrawEl[0], (double) toDrawEl[i + 1]);
            while (it.hasNext()) {
                toDrawEl = it.next();
                gc.lineTo((double) toDrawEl[0], (double) toDrawEl[i + 1]);
            }
            gc.stroke();
            gc.closePath();
        }
    }
    
    public float getAutoTickCount() {
        return autoTickCount;
    }
    
    public void setAutoTickCount(float autoTickCount) {
        this.autoTickCount = autoTickCount;
    }

    public void setShowScaleBox(boolean showScaleBox0) {
        showScaleBox = showScaleBox0;
        setShowNode(showScaleBox, scaleBox);
    }

    public boolean isShowScaleBox() {
        return showScaleBox;
    }

    public void setShowXRuler(boolean showXRuler0) {
        showXRuler = showXRuler0;
        setShowNode(showXRuler, xRuler);
    }

    public boolean isShowXRuler() {
        return showXRuler;
    }

    public void setShowYRuler(boolean showYRuler0) {
        showYRuler = showYRuler0;
        setShowNode(showYRuler, yRuler);
    }

    public boolean isShowYRuler() {
        return showYRuler;
    }

    public void setShowScrollBar(boolean showScrollBar0) {
        showScrollBar = showScrollBar0;
        setShowNode(showScrollBar, scrollBar);
    }

    public boolean isShowScrollBar() {
        return showScrollBar;
    }

    public void setShowLegendBox(boolean showLegendBox0) {
        showLegendBox = showLegendBox0;
        setShowNode(showLegendBox, legendBoxBase);
    }

    public boolean isShowLegendBox() {
        return showLegendBox;
    }

    private void updateDataShow() {
        legendBox.getChildren().clear();
        for (Field f : fields) {
            legendBox.getChildren().add(f.cbData);
            legendBox.getChildren().add(f.txtData);
        }
    }

    /**
     * @return the nFields
     */
    public int getnFields() {
        return nFields;
    }

    /**
     * @param nFields the nFields to set
     */
    public final void setnFields(int nFields) {
        synchronized (dataLock) {
            if (!data.isEmpty())
                return;
            this.nFields = nFields;
            fields = new Field[nFields];
            for (int i = 0; i < nFields; i++) {
                fields[i] = new Field();
                
                CheckBox cb = new CheckBox();
                cb.setSelected(true);
                fields[i].cbData = cb;
                
                Label l =  new Label(i < 3 ? DEFAULT_NAMES[i] : "");
                Pane p = new Pane();
                p.getStyleClass().add("default-color" + (i%8));
                p.getStyleClass().add("chart-line-symbol");
                p.setPrefHeight(10);
                p.setPrefWidth(10);
                l.setGraphic(p);
                fields[i].txtData = l;
            }
            updateDataShow();
            setDefaultStyle();
        }
    }

    private void setShowNode(boolean flag, Node node) {
        if (flag) {
            if (!gridPane.getChildren().contains(node)) {
                gridPane.getChildren().add(node);
            }
        } else {
            gridPane.getChildren().remove(node);
        }
    }

    public String getFinalName() {
        return (name != null && !name.isEmpty()) ? name : "" + hashCode();
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
        //cbAutoscale.setSelected(PropertiesLoader.getDefault().getProperty("linegraph_" + name + "_autoscale", "false").equals("true"));
        //enableDump = PropertiesLoader.getDefault().getProperty("linegraph_" + name + "_enabledump", "true").equals("true");
    }
    
    public long loadCacheData(long from, long to, Consumer<Object[]> consumer) throws IOException {
        FileInputStream fis;
        long total;
        synchronized (fileDumpLock) {
            File f = new File(getCachePath());
            if (!f.exists() || !f.canRead())
                return 0;
            
            fis = new FileInputStream(f);
            ByteBuffer buff = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            int n = buff.getInt();
            int chunkSize = 8 + 4 * n;
            total = f.length() / chunkSize;
            buff = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN);
            int remaining;
            int count = 0;
            while ((remaining = fis.available()) > 0) {
                if (remaining < chunkSize) {
                    break;
                }
                fis.read(buff.array());
                buff.position(0);
                Object[] tmp = new Object[n + 1];
                long timestamp = buff.getLong();
                
                if (to != -1 && timestamp > to) {
                    total -= ((fis.available() / chunkSize) - 1);
                    break;
                }
                if (from != -1 && timestamp < from) {
                    total--;
                    continue;
                }
                
                tmp[0] = timestamp;
                for (int i = 0; i < n; i++)
                    tmp[i + 1] = buff.getFloat();
                
                count++;
                if (total <= 0)
                    total = 1;
                
                consumer.accept(new Object[] {count, total, tmp});
            }
            fis.close();
        }
        return total;
    }

    public ArrayList<Object[]> loadCacheData(long from, long to) throws IOException {
        ArrayList<Object[]> cacheData = new ArrayList<>();
        loadCacheData(from, to, (Object[] datum) -> {
            cacheData.add(datum);
        });
        return cacheData;
    }

    public void loadCsv(String path, boolean useHeader, int[] totalLine) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(path));
        if (useHeader) {
            sc.nextLine();
        }
        synchronized (dataLock) {
            while (sc.hasNextLine()) {
                try {
                    String[] valStr = sc.nextLine().split(",");
                    Object[] d = new Object[]{
                        Long.parseLong(valStr[0]),
                        Float.parseFloat(valStr[1]),
                        Float.parseFloat(valStr[2]),
                        Float.parseFloat(valStr[3]),};
                    addData(d);
                } catch (NumberFormatException ex) {
                    totalLine[1]++;
                }
                totalLine[0]++;
            }
        }
    }

    /**
     * @return the startMs
     */
    public long getStartMs() {
        return startMs;
    }
    
    public Object[] getLast() {
        return get(-1);
    }
    
    public Object[] getFirst() {
        return get(0);
    }
    
    public Object[] get(int idx) {
        Object[] result = null;
        synchronized (dataLock) {
            if (!data.isEmpty())
                result = data.get(idx >= 0 ? idx : data.size() + idx);
        }
        return result;
    }

    /**
     * @return the yMin
     */
    public float getyMin() {
        return yMin;
    }

    /**
     * @param yMin the yMin to set
     */
    public final void setyMin(float yMin) {
        this.yMin = yMin;
    }

    /**
     * @return the yMax
     */
    public float getyMax() {
        return yMax;
    }

    /**
     * @param yMax the yMax to set
     */
    public final void setyMax(float yMax) {
        this.yMax = yMax;
    }

    /**
     * @return the yUnitTick
     */
    public float getyUnitTick() {
        return yUnitTick;
    }

    /**
     * @param yUnitTick the yUnitTick to set
     */
    public final void setyUnitTick(float yUnitTick) {
        this.yUnitTick = yUnitTick;
    }

    /**
     * @return the xUnitTick
     */
    public float getxUnitTick() {
        return xUnitTick;
    }

    /**
     * @param xUnitTick the xUnitTick to set
     */
    public final void setxUnitTick(float xUnitTick) {
        this.xUnitTick = xUnitTick;
    }

    /**
     * @return the widthPerSec
     */
    public double getWidthPerSec() {
        return widthPerSec;
    }

    /**
     * @param widthPerSec the widthPerSec to set
     */
    public final void setWidthPerSec(double widthPerSec) {
        this.widthPerSec = widthPerSec;
        widthPerMs = widthPerSec / 1000;
    }

    /**
     * @return the data
     */
    public ArrayList<Object[]> getData() {
        return data;
    }
    
    /**
     * @param enable set enabled or not
     */
    public void setAutoscale(boolean enable) {
        cbAutoscale.setSelected(enable);
    }
    
    /**
     * @return is autoscale enabled
     */
    public boolean isAutoscale() {
        return cbAutoscale.isSelected();
    }

    /**
     * @return the fields
     */
    public Field[] getFields() {
        return fields;
    }
}