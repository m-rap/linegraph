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

import com.mrap.common.FxScheduler;
import com.mrap.common.BaseService;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
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

    static final int CLEAR_DATA_MS = 5 * 60 * 1000; // 5 menit
    private long DUMP_INTERVAL_MS = 60 * 1000;
    static float MIN_AUTOTICK = 0.005f;
    final static float DEFAULT_TICK_COUNT = 5.0f;

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
    CheckBox cbData0;
    @FXML
    CheckBox cbData1;
    @FXML
    CheckBox cbData2;
    @FXML
    CheckBox cbAutoscale;

    @FXML
    TextField txtYMin;
    @FXML
    TextField txtYMax;
    @FXML
    TextField txtYUnitTick;
    @FXML
    TextField txtXUnitTick;
    @FXML
    TextField txtWidthPerSec;

    @FXML
    Label txtData0;
    @FXML
    Label txtData1;
    @FXML
    Label txtData2;

    public BaseService runnable = new BaseService(1000/120) {
        @Override
        public String getName() {
            return "LineGraph runnable";
        }

        @Override
        public void onStart() {

        }

        @Override
        public void onStop() {
            dump();
        }

        @Override
        public void onRun() {
            if (isVisible() && getParent() != null) {
                FxScheduler.checkAndRun(() -> {
                    long[] showStartEnd = new long[2];
                    ArrayDeque<float[]> toDraw = createToDraw(showStartEnd);
                    display(toDraw, showStartEnd[0], showStartEnd[1]);
                }, FxScheduler.RUNNABLE_PRIORITY_LOW);
            }
            dump();
            //try {
            //    Thread.sleep((long) delay - 1);
            //} catch (InterruptedException ex) {
            //    Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
            //}
            //FutureTask f = new FutureTask(new Callable() {
            //    @Override
            //    public Object call() throws Exception {
            //        display(toDraw, showStartEnd[0], showStartEnd[1]);
            //        return null;
            //    }
            //});
            //if (Platform.isFxApplicationThread()) {
            //    f.run();
            //} else {
            //    FxRunnableManager.checkAndRun(f);
            //    try {
            //        f.get();
            //        dump();
            //        Thread.sleep((long) delay - 1);
            //    } catch (InterruptedException ex) {
            //    } catch (ExecutionException ex) {
            //        Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
            //    }
            //}
        }

        @Override
        public void join() {
            if (t == null || Platform.isFxApplicationThread()) {
                return;
            }
            try {
                t.interrupt();
                t.join();
            } catch (InterruptedException ex) {
                Logger.getLogger(BaseService.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    };
    
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
    float yMin, yMax, yUnitTick, xUnitTick, widthPerSec;
    float autoYMin, autoYMax;
    private final ArrayList<Object[]> data = new ArrayList<>();
    long last;
    float fps;
    float delay;
    GraphicsContext gc;
    private double lineWidth;
    private Paint[] colors;
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
    private boolean showData0 = true;
    private boolean showData1 = true;
    private boolean showData2 = true;
    CheckBox[] cbData;
    private float widthPerMs;
    private final Object fileDumpLock = new Object();
    protected String name = "";
    File dir = new File(".linegraph");

    private Runnable dumpRunnable = new Runnable() {
        @Override
        public void run() {
            ArrayDeque<Object[]> temp;
            synchronized (data) {
                long a = (long)data.get(0)[0], b = (long)data.get(data.size() - 1)[0];
                log("start dumping from " + (long)data.get(dumpStartIdx)[0] + 
                        ", current data size,start,end,length " + data.size() + "," + a +
                        "," + b + "," + (b - a));
                dumpEndIdx = data.size() - 1;
                temp = new ArrayDeque<>();
                for (; dumpStartIdx <= dumpEndIdx; dumpStartIdx++) {
                    temp.add(data.get(dumpStartIdx));
                }
            }

            if (!temp.isEmpty()) {
                synchronized (fileDumpLock) {
                    try {
                        if (!dir.exists()) {
                            Files.createDirectory(dir.toPath());
                        }

                        File file = new File(getCachePath());
                        FileOutputStream fos = new FileOutputStream(file, true);
                        for (Object[] datum : temp) {
                            ByteBuffer buff = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
                            buff.putLong((long) datum[0]);
                            buff.putFloat((float) datum[1]);
                            buff.putFloat((float) datum[2]);
                            buff.putFloat((float) datum[3]);
                            fos.write(buff.array());
                        }
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
        try {
            FXMLLoader loader = new FXMLLoader(LineGraph.class.getResource("/fxml/LineGraph.fxml"));
            loader.setRoot(this);
            loader.setController(this);
            loader.load();
        } catch (IOException ex) {
            Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
        }

        autoTickCount = DEFAULT_TICK_COUNT;
        sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        
        //cbAutoscale.selectedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
        //    
        //    if (name != null && !name.isEmpty())
        //        PropertiesLoader.getDefault().setProperty("linegraph_" + name + "_autoscale", newValue ? "true" : "false");
        //});

        cbData = new CheckBox[]{cbData0, cbData1, cbData2};
        scrollRect = (Rectangle) scrollBar.getChildren().get(0);
        gc = canvas.getGraphicsContext2D();

        setDefaultStyle();

        setScale(yMin1, yMax1, yUnitTick1, xUnitTick1, widthPerSec1);

        fps = 30;
        delay = 1000 / fps;

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
    }
    
    //public void setIsDrawChartOnSave(boolean isDrawChartOnSave) {
    //    this.isDrawChartOnSave = isDrawChartOnSave;
    //}

    public void dump() {
        synchronized (data) {
            if (data.isEmpty()) {
                return;
            }
            if (dumpStartIdx >= data.size() || dumpStartIdx < 0) {
                return;
            }
            if (isDumping) {
                return;
            }
            if ((long) data.get(data.size() - 1)[0] - (long) data.get(dumpStartIdx)[0] > DUMP_INTERVAL_MS) {
                isDumping = true;
                internalDump();
            }
        }
    }

    private void internalDump() {
        Thread t = new Thread(dumpRunnable);
        t.start();
    }

    public void setLegends(String[] names) {
        txtData0.setText(names[0]);
        txtData1.setText(names[1]);
        txtData2.setText(names[2]);
    }

    @FXML
    void onScaleSet(ActionEvent e) {
        try {
            setScale(Float.parseFloat(txtYMin.getText()),
                    Float.parseFloat(txtYMax.getText()),
                    Float.parseFloat(txtYUnitTick.getText()),
                    Float.parseFloat(txtXUnitTick.getText()),
                    Float.parseFloat(txtWidthPerSec.getText()));
        } catch (NumberFormatException ex) {
            txtYMin.setText("" + yMin);
            txtYMax.setText("" + yMax);
            txtYUnitTick.setText("" + yUnitTick);
            txtXUnitTick.setText("" + xUnitTick);
            txtWidthPerSec.setText("" + widthPerSec);
        }
    }

    public void setScale(float yMin1, float yMax1, float yUnitTick1, float xUnitTick1, float widthPerSec1) {
        yMin = yMin1;
        yMax = yMax1;
        yUnitTick = yUnitTick1;
        xUnitTick = xUnitTick1;
        widthPerSec = widthPerSec1;
        widthPerMs = widthPerSec / 1000;

        txtYMin.setText("" + yMin);
        txtYMax.setText("" + yMax);
        txtYUnitTick.setText("" + yUnitTick);
        txtXUnitTick.setText("" + xUnitTick);
        txtWidthPerSec.setText("" + widthPerSec);
    }

    private void setDefaultStyle() {
        Pane paneAux = new Pane();
        Scene sceneAux = new Scene(paneAux);
        colors = new Paint[3];
        Line line = new Line();
        line.getStyleClass().add("default-color0");
        line.getStyleClass().add("chart-series-line");
        paneAux.getChildren().clear();
        paneAux.getChildren().add(line);
        line.applyCss();
        colors[0] = line.getStroke();
        lineWidth = line.getStrokeWidth();
        line = new Line();
        line.getStyleClass().add("default-color1");
        line.getStyleClass().add("chart-series-line");
        paneAux.getChildren().clear();
        paneAux.getChildren().add(line);
        line.applyCss();
        colors[1] = line.getStroke();
        line = new Line();
        line.getStyleClass().add("default-color2");
        line.getStyleClass().add("chart-series-line");
        paneAux.getChildren().clear();
        paneAux.getChildren().add(line);
        line.applyCss();
        colors[2] = line.getStroke();
    }

    private void onResize(double width, double height) {
        canvas.setWidth(width);
        canvas.setHeight(height);
        //runnable.onRun();
    }
    
    ArrayDeque<Label> lbls = new ArrayDeque<>();
    final static Font AXIS_FONT = new Font("monospaced", 10);
    final static DecimalFormat DF = new DecimalFormat("#.###");

    private void drawAxisY() {
        double width = canvas.getWidth();
        double height = canvas.getHeight();
        
        for (Node n : yRuler.getChildren())
            if (n instanceof Label && !lbls.contains(n))
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
            //if (scaledActualY < 1 || scaledActualY > height - 1) {
            //    continue;
            //}
            if (gridPane.getChildren().contains(yRuler)) {
                Label text;
                if (!lbls.isEmpty())
                    text = lbls.pop();
                else
                    text = new Label();
                if (tmpYUnitTick < 1.0f)
                    text.setText(DF.format(y));
                    //text.setText(String.format("%.3f", y));
                else
                    text.setText(String.format("%.0f", y));
                text.setFont(AXIS_FONT);
                //text.setStyle("-fx-background-color: red;");
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
        //if (width != canvas.getWidth() || height != canvas.getHeight())
        //    System.out.println(canvas.getWidth() + " " + canvas.getHeight());
    }

    private void drawAxisX(long showStart, long showEnd) {
        for (Node n : xRuler.getChildren())
            if (n instanceof Label && !lbls.contains(n))
                lbls.add((Label)n);
        xRuler.getChildren().clear();
        long x = (long) ((float) Math.ceil(showStart / (xUnitTick * 1000)) * xUnitTick * 1000);
        double y2 = canvas.getHeight();
        while (x < showEnd) {
            //double scaledActualX = (x - xStart) * widthPerSec;
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
                //text.setStyle("-fx-background-color: red;");
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
        //double w = canvas.getWidth(), h = canvas.getHeight();
        //gridPane.applyCss();
        //gridPane.layout();
        //if (w != canvas.getWidth() || h != canvas.getHeight())
        //    System.out.println(canvas.getWidth() + " " + canvas.getHeight());
    }

    public void addData(Object... datum) {
        if (isSaving) {
            return;
        }
        synchronized (data) {
            if (startMs == -1) {
                startMs = (long) datum[0];
            }
            data.add(datum);
            boolean autoscaleChanged = false;
            if (showData0) {
                if ((float) datum[1] < dataYMin) {
                    dataYMin = (float) datum[1];
                    autoscaleChanged = true;
                }
                if ((float) datum[1] > dataYMax) {
                    dataYMax = (float) datum[1];
                    autoscaleChanged = true;
                }
            }
            if (showData1) {
                if ((float) datum[2] < dataYMin) {
                    dataYMin = (float) datum[2];
                    autoscaleChanged = true;
                }
                if ((float) datum[2] > dataYMax) {
                    dataYMax = (float) datum[2];
                    autoscaleChanged = true;
                }
            }
            if (showData2) {
                if ((float) datum[3] < dataYMin) {
                    dataYMin = (float) datum[3];
                    autoscaleChanged = true;
                }
                if ((float) datum[3] > dataYMax) {
                    dataYMax = (float) datum[3];
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

            long timeLength = 0, a = 0, b = 0;
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

            if (enableDump)
                dump();
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
    
    public void saveData(final long from, final long to, final boolean csv,
            final Consumer<Object> progressConsumer, final String path,
            final String... headers) {
        Thread t = new Thread(() -> {
            runnable.stop();
            isSaving = true;
            try {
                File f = new File(path);
                if (f.exists()) {
                    throw new Exception("Saving failed: File exists");
                }
                FileWriter fw = null;
                
                if (csv) {
                    fw = new FileWriter(f);
                    if (headers != null) {
                        for (int index = 0; index < headers.length; index++) {
                            if (index != 0) {
                                fw.write(",");
                            }
                            fw.write(headers[index]);
                        }
                        fw.write("\r\n");
                    }
                }
                
                ArrayList<Object[]> tempData = new ArrayList<>();
                long total;
                int counter = 0;
                synchronized (data) {
                    synchronized (fileDumpLock) {
                        tempData.addAll(data.subList(dumpedEndIdx + 1, data.size()));
                        total = tempData.size();
                        ArrayList<Path> list = new ArrayList<>();
                        File cacheFile = new File(getCachePath());
                        if (cacheFile.exists() && cacheFile.canRead()) {
                            FileInputStream is = new FileInputStream(cacheFile);
                            ByteBuffer buffer = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
                            total += (is.available() / 20);
                            while (is.available() >= 20) {
                                buffer.position(0);
                                is.read(buffer.array());
                                long timestamp = buffer.getLong();
                                if (to != -1 && timestamp > to) {
                                    total -= ((is.available() / 20) - 1);
                                    break;
                                }
                                if (from != -1 && timestamp < from) {
                                    total--;
                                    continue;
                                }
                                float data1 = buffer.getFloat();
                                float data2 = buffer.getFloat();
                                float data3 = buffer.getFloat();
                                
                                if (csv) {
                                    fw.write(timestamp + ",");
                                    fw.write(data1 + ",");
                                    fw.write(data2 + ",");
                                    fw.write(data3 + "\r\n");
                                }
                                
                                /*
                                fw.write(buffer.getLong() + ",");
                                fw.write(buffer.getFloat() + ",");
                                fw.write(buffer.getFloat() + ",");
                                fw.write(buffer.getFloat() + "\r\n");
                                */
                                counter++;
                                if (total <= 0)
                                    total = 1;
                                if (progressConsumer != null && counter < total) {
                                    progressConsumer.accept((double) counter / total);
                                }
                            }
                            is.close();
                        }
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
                    float data1 = (float) row[1];
                    float data2 = (float) row[2];
                    float data3 = (float) row[3];
                    
                    if (csv) {
                        fw.write(row[0].toString() + ",");
                        fw.write(row[1].toString() + ",");
                        fw.write(row[2].toString() + ",");
                        fw.write(row[3].toString() + "\r\n");
                    }
                    counter++;
                    if (total <= 0)
                        total = 1;
                    if (progressConsumer != null && counter < total) {
                        progressConsumer.accept((double) counter / total);
                    }
                    i++;
                }
                if (csv)
                    fw.close();
                
                if (progressConsumer != null) {
                    progressConsumer.accept((double) 1);
                }
            } catch (Exception ex) {
                Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                progressConsumer.accept(ex);
            }
            isSaving = false;
            try {
                runnable.start();
            } catch (Exception ex) {
                Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
            }
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
        synchronized (data) {
            data.clear();
            startMs = -1;
            dataYMin = Float.MAX_VALUE;
            dataYMax = Float.MIN_VALUE;
            autoYUnitTick = 1;
            currIdx = 0;
            startMs = -1;
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
        synchronized (data) {
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
            boolean firstFound = false;
            boolean lastFound = false;
            int prevX = Integer.MIN_VALUE;
            long ts = 0;

            if (!data.isEmpty()) {
                if (currIdx > data.size() - 1) {
                    currIdx = data.size() - 1;
                }
                int tempCurrIdx = currIdx;
                for (; tempCurrIdx >= 0; tempCurrIdx--) {
                    Object[] datum = data.get(tempCurrIdx);
                    ts = (long) datum[0] - start;
                    if (ts < showStart0) {
                        break;
                    }
                }
                currIdx = (tempCurrIdx < 0) ? 0 : tempCurrIdx;
            }
            
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
            
            for (int tempCurrIdx = currIdx; tempCurrIdx < data.size(); tempCurrIdx++) {
                Object[] datum = data.get(tempCurrIdx);
                ts = (long) datum[0] - start;
                float scaledX = (float) Math.floor(ts * widthPerMs);
                if ((int) scaledX == prevX) {
                    continue;
                }
                prevX = (int) scaledX;
                if ((!firstFound && ts >= showStart0 && ts <= showEnd)
                        || (firstFound && ts <= showEnd && !lastFound)
                        || lastFound) {
                    if (!firstFound) {
                        firstFound = true;
                        showStart0 = ts;
                        showEnd = showStart0 + totalShowMs;
                        if (tempCurrIdx > 0) {
                            tempCurrIdx--;
                            datum = data.get(tempCurrIdx);
                            ts = (long) datum[0] - start;
                            scaledX = (float) Math.floor(ts * widthPerMs);
                        }
                        currIdx = tempCurrIdx;
                    }
                    float[] toDrawEl = new float[4];
                    toDrawEl[0] = scaledX - startPx;
                    for (int i = 0; i < datum.length - 1; i++) {
                        if (!cbData[i].isSelected()) {
                            continue;
                        }
                        float scaledY = -((float) datum[i + 1] - tmpYMax) * (float) canvas.getHeight() / (tmpYMax - tmpYMin);
                        toDrawEl[i + 1] = scaledY;
                    }
                    toDraw.add(toDrawEl);
                    if (lastFound) {
                        break;
                    }
                } else if (!lastFound && ts > showEnd) {
                    lastFound = true;
                    tempCurrIdx--;
                } else if (ts > showEnd) {
                    break;
                }
            }
        }
        showStartEnd[0] = showStart0;
        showStartEnd[1] = showEnd;
        return toDraw;
    }

    void display(ArrayDeque<float[]> toDraw, long showStart0, long showEnd) {
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
        for (int i = 0; i < 3; i++) {
            if (!cbData[i].isSelected()) {
                continue;
            }
            if (i == 0 && !showData0) {
                continue;
            }
            if (i == 1 && !showData1) {
                continue;
            }
            if (i == 2 && !showData2) {
                continue;
            }

            gc.setStroke(colors[i]);
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

    /**
     * @return the showData0
     */
    public boolean isShowData0() {
        return showData0;
    }

    /**
     * @param showData0 the showData0 to set
     */
    public void setShowData0(boolean showData0) {
        this.showData0 = showData0;
        updateDataShow();
    }

    /**
     * @return the showData1
     */
    public boolean isShowData1() {
        return showData1;
    }

    /**
     * @param showData1 the showData1 to set
     */
    public void setShowData1(boolean showData1) {
        this.showData1 = showData1;
        updateDataShow();
    }

    /**
     * @return the showData2
     */
    public boolean isShowData2() {
        return showData2;
    }

    /**
     * @param showData2 the showData2 to set
     */
    public void setShowData2(boolean showData2) {
        this.showData2 = showData2;
        updateDataShow();
    }

    void updateDataShow() {
        legendBox.getChildren().clear();
        if (showData0) {
            legendBox.getChildren().add(cbData0);
            legendBox.getChildren().add(txtData0);
        }
        if (showData1) {
            legendBox.getChildren().add(cbData1);
            legendBox.getChildren().add(txtData1);
        }
        if (showData2) {
            legendBox.getChildren().add(cbData2);
            legendBox.getChildren().add(txtData2);
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

    public ArrayList<Object[]> loadCacheData() throws IOException {
        FileInputStream fis;
        ArrayList<Object[]> cacheData = new ArrayList<>();
        synchronized (fileDumpLock) {
            try {
                fis = new FileInputStream(getCachePath());
                int remaining;
                while ((remaining = fis.available()) > 0) {
                    if (remaining < 20) {
                        break;
                    }
                    ByteBuffer buff = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
                    fis.read(buff.array());
                    buff.position(0);
                    Object[] tmp = new Object[4];
                    tmp[0] = buff.getLong();
                    tmp[1] = buff.getFloat();
                    tmp[2] = buff.getFloat();
                    tmp[3] = buff.getFloat();
                    cacheData.add(tmp);
                }
                fis.close();
            } catch (FileNotFoundException ex) {
            }
        }
        return cacheData;
    }

    public void loadCsv(String path, boolean useHeader, int[] totalLine) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(path));
        if (useHeader) {
            sc.nextLine();
        }
        synchronized (data) {
            while (sc.hasNextLine()) {
                try {
                    String[] valStr = sc.nextLine().split(",");
                    Object[] data = new Object[]{
                        Long.parseLong(valStr[0]),
                        Float.parseFloat(valStr[1]),
                        Float.parseFloat(valStr[2]),
                        Float.parseFloat(valStr[3]),};
                    addData(data);
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
        synchronized (data) {
            if (!data.isEmpty())
                result = data.get(idx >= 0 ? idx : data.size() + idx);
        }
        return result;
    }
}