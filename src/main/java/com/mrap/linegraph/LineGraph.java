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

import com.mrap.data.CacheableData;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.Iterator;
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

    static float MIN_AUTOTICK = 0.005f;
    final static float DEFAULT_TICK_COUNT = 5.0f;
    private static final String[] DEFAULT_NAMES = {"X", "Y", "Z"};
    public static final float END_GAP = 0.9f;

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
    
    private int fieldCount = 0;
    private Field[] fields;
    
    float dataYMin = Float.MAX_VALUE, dataYMax = Float.MIN_VALUE, autoYUnitTick = 1;
    private int currIdx = 0;
    boolean scrollBarLocked = false;
    long showStart = -1;
    private float autoTickCount = DEFAULT_TICK_COUNT;

    Rectangle scrollRect;
    private double minY;
    private double maxY;
    private double unitTickY;
    private double unitTickX;
    private double widthPerSec;
    private double widthPerMs;
    
    float autoYMin, autoYMax;
    private CacheableData cacheableData;
    GraphicsContext gc;
    private double lineWidth;
    
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
    
    String debugStr = "";
    
    final LineGraph that = this;
    
    CacheableData.ChangeListener changedListener = new CacheableData.ChangeListener() {
        @Override
        public void onAdd() {
            //for (Object[] datum : c.getAddedSubList())
            final CacheableData dataFinal = cacheableData;
            //synchronized (dataFinal) {
                Object[] datum = dataFinal.get(dataFinal.size() - 1);
                that.onAdd(datum);
            //}
        }

        @Override
        public void onReset() {
            resetIndices();
        }
    };
    
    AnimationTimer fxTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (!isVisible() || getParent() == null)
                return;
            try {
                display();
            } catch (Exception ex) {
                Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    };

    public LineGraph getThis() {
        return this;
    }

    public LineGraph() {
        this(-5, 5, 1, 1, 0, 20);
    }

    public LineGraph(float minY1, float maxY1, float unitTickY1, float unitTickX1, int nfields, float widthPerSec1) {
        this(minY1, maxY1, unitTickY1, unitTickX1, widthPerSec1, nfields, new CacheableData());
    }
    
    public LineGraph(float minY1, float maxY1, float unitTickY1, float unitTickX1, float widthPerSec1, int nfields, CacheableData data1) {
        try {
            FXMLLoader loader = new FXMLLoader(LineGraph.class.getResource("/fxml/LineGraph.fxml"));
            loader.setRoot(this);
            loader.setController(this);
            loader.load();
        } catch (IOException ex) {
            Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
        }

        //data = data1;
        cacheableData = data1;
        cacheableData.addListener(changedListener);
        autoTickCount = DEFAULT_TICK_COUNT;
        
        setFieldCount(nfields);
        setMinY(minY1);
        setMaxY(maxY1);
        setUnitTickY(unitTickY1);
        setUnitTickX(unitTickX1);
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
        setMinY(yMin1);
        setMaxY(yMax1);
        setUnitTickY(yUnitTick1);
        setUnitTickX(xUnitTick1);
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
        double tmpYMin, tmpYMax, tmpYUnitTick;
        if (cbAutoscale.isSelected() && dataYMin < Float.MAX_VALUE) {
            tmpYMin = autoYMin;
            tmpYMax = autoYMax;
            tmpYUnitTick = autoYUnitTick;
        } else {
            tmpYMin = minY;
            tmpYMax = maxY;
            tmpYUnitTick = unitTickY;
        }
        double y = (int) (tmpYMin / tmpYUnitTick) * tmpYUnitTick + tmpYUnitTick;
        for (; y <= tmpYMax; y += tmpYUnitTick) {
            double actualY = tmpYMax - y;
            double scaledActualY = actualY * height / (tmpYMax - tmpYMin);
            if (gridPane.getChildren().contains(yRuler)) {
                Label text;
                if (!lbls.isEmpty())
                    text = lbls.pop();
                else {
                    text = new Label();
                }
                text.setText(DF.format(y));
                text.setFont(AXIS_FONT);
                text.applyCss();
                if (scaledActualY - 5 + text.getBoundsInLocal().getHeight() < yRuler.getBoundsInLocal().getHeight() &&
                        scaledActualY - 5 > 0) {
                    AnchorPane.setBottomAnchor(text, null);
                    AnchorPane.setLeftAnchor(text, null);
                    AnchorPane.setTopAnchor(text, scaledActualY - 5);
                    AnchorPane.setRightAnchor(text, 3.0);
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
        long x = (long) ((float) Math.ceil(showStart / (unitTickX * 1000)) * unitTickX * 1000);
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
                float displayX;
                if (cacheableData.getStartMs() == -1) {
                    displayX = (float)x / 1000;
                } else {
                    displayX = (float)(x + ((long) cacheableData.get(0)[0] -
                            cacheableData.getStartMs())) / 1000;
                }
                text.setText(DF.format(displayX));
                text.setFont(AXIS_FONT);
                text.applyCss();
                if (scaledActualX + text.getBoundsInLocal().getWidth() < xRuler.getWidth() &&
                        scaledActualX > 0) {
                    AnchorPane.setRightAnchor(text, null);
                    AnchorPane.setBottomAnchor(text, null);
                    AnchorPane.setTopAnchor(text, 3.0);
                    AnchorPane.setLeftAnchor(text, scaledActualX);
                    xRuler.getChildren().add(text);
                } else {
                    xRuler.getChildren().remove(text);
                }
            }
            x += (unitTickX * 1000);
        }
    }
    
    public void setData(final CacheableData data) {
        synchronized (data) {
            resetIndices();
            cacheableData.removeListener(changedListener);
            cacheableData = data;
            cacheableData.addListener(changedListener);
            for (Object[] datum : data) {
                onAdd(datum);
            }
        }
    }
    
    private void onAdd(Object[] datum) {        
        boolean autoscaleChanged = false;
        
        for (int i = 0; i < datum.length - 1; i++) {
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
    
    private void resetIndices() {
        dataYMin = Float.MAX_VALUE;
        dataYMax = Float.MIN_VALUE;
        autoYUnitTick = 1;
        currIdx = 0;
        scrollBarLocked = false;
        showStart = -1;
        autoTickCount = DEFAULT_TICK_COUNT;
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
    
    public long getTotalShowMs() {
        return totalShowMs;
    }
    
    ArrayDeque<float[]> createToDraw(long[] showStartEnd) {
        ArrayDeque<float[]> toDraw = new ArrayDeque<>();
        long showStart0, start, end, showEnd;
        final CacheableData dataFinal = cacheableData;
        synchronized (dataFinal) {
            showStart0 = showStart;
            start = (!dataFinal.isEmpty()) ? (long) dataFinal.get(0)[0] : 0;
            end = (!dataFinal.isEmpty()) ? (long) dataFinal.get(dataFinal.size() - 1)[0] : 0;
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
            
            if (dataFinal.isEmpty())
                return toDraw;
            
            //debugStr = new StringBuilder().append(this.showStart).append(" ").
            //        append(showStart0).append(" ").append(showEnd).toString();
            
            int prevX = Integer.MIN_VALUE;
            long ts;
            
            double tmpYMin, tmpYMax;
            if (cbAutoscale.isSelected() && dataYMin < Float.MAX_VALUE) {
                tmpYMin = autoYMin;
                tmpYMax = autoYMax;
            } else {
                tmpYMin = minY;
                tmpYMax = maxY;
            }
            
            for (; currIdx >= 0; currIdx--) {
                Object[] datum = dataFinal.get(currIdx);
                ts = (long) datum[0] - start;
                if (ts < showStart0)
                    break;
            }
            if (currIdx < 0) currIdx = 0;
            
            for (; currIdx < dataFinal.size(); currIdx++) {
                Object[] datum = dataFinal.get(currIdx);
                ts = (long) datum[0] - start;
                if (ts >= showStart0) {
                    break;
                }
            }
            if (currIdx > 0) currIdx--;
            
            for (int tempCurrIdx = currIdx; tempCurrIdx < dataFinal.size(); tempCurrIdx++) {
                Object[] datum = dataFinal.get(tempCurrIdx);
                if (fieldCount == 0) {
                    setFieldCount(datum.length - 1);
                }
                ts = (long) datum[0] - start;
                float scaledX = (float) Math.floor(ts * widthPerMs);
                
                if ((int) scaledX == prevX) {
                    continue;
                }
                prevX = (int) scaledX;
                    
                float[] toDrawEl = new float[fieldCount + 1];
                toDrawEl[0] = scaledX - startPx;
                int i;
                for (i = 0; i < datum.length - 1 && i < fieldCount; i++) {
                    if (!fields[i].cbData.isSelected()) {
                        continue;
                    }
                    float scaledY = -((float) datum[i + 1] - (float) tmpYMax) *
                            (float) canvas.getHeight() / (float) (tmpYMax - tmpYMin);
                    toDrawEl[i + 1] = scaledY;
                }
                for (; i < fieldCount - 1; i++) {
                    toDrawEl[i + 1] = 0;
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
        final CacheableData dataFinal = cacheableData;
        synchronized (dataFinal) {
            if (fields == null)
                return;
        }
        
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
        for (int i = 0; i < fieldCount; i++) {
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
    
    public void setShowAutoscale(boolean showAutoScale) {
        cbAutoscale.setVisible(showAutoScale);
    }

    public boolean isShowAutoscale() {
        return cbAutoscale.isVisible();
    }

    private void updateDataShow() {
        legendBox.getChildren().clear();
        for (Field f : fields) {
            legendBox.getChildren().add(f.cbData);
            legendBox.getChildren().add(f.txtData);
        }
    }

    /**
     * @return the fieldCount
     */
    public int getFieldCount() {
        return fieldCount;
    }

    /**
     * @param fieldCount the fieldCount to set
     */
    public final void setFieldCount(int fieldCount) {
        //ArrayList<Object[]> dataFinal = data;
        final CacheableData dataFinal = cacheableData;
        synchronized (dataFinal) {
            this.fieldCount = fieldCount;
            fields = new Field[fieldCount];
            for (int i = 0; i < fieldCount; i++) {
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
    
    /**
     * @return the names
     */
    public String getFieldNames() {
        //ArrayList<Object[]> dataFinal = data;
        final CacheableData dataFinal = cacheableData;
        synchronized (dataFinal) {
            if (fields == null)
                return "";
            String[] names = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                names[i] = fields[i].txtData.getText();
            }
            return String.join(":", names);
        }
    }

    /**
     * @param names the fieldNames to set
     */
    public void setFieldNames(String names) {
        final CacheableData dataFinal = cacheableData;
        synchronized (dataFinal) {
            if (fields == null)
                return;
            String[] namesArr = names.split(":");
            int i;
            for (i = 0; i < namesArr.length && i < fields.length; i++) {
                fields[i].txtData.setText(namesArr[i]);
            }
            for (; i < DEFAULT_NAMES.length && i < fields.length; i++) {
                fields[i].txtData.setText(DEFAULT_NAMES[i]);
            }
            for (; i < fields.length && i < fields.length; i++) {
                fields[i].txtData.setText("");
            }
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

    /**
     * @return the yMin
     */
    public double getMinY() {
        return minY;
    }

    /**
     * @param minY the yMin to set
     */
    public final void setMinY(double minY) {
        this.minY = minY;
    }

    /**
     * @return the maxY
     */
    public double getMaxY() {
        return maxY;
    }

    /**
     * @param maxY the maxY to set
     */
    public final void setMaxY(double maxY) {
        this.maxY = maxY;
    }

    /**
     * @return the unitTickY
     */
    public double getUnitTickY() {
        return unitTickY;
    }

    /**
     * @param unitTickY the unitTickY to set
     */
    public final void setUnitTickY(double unitTickY) {
        this.unitTickY = unitTickY;
    }

    /**
     * @return the unitTickX
     */
    public double getUnitTickX() {
        return unitTickX;
    }

    /**
     * @param unitTickX the unitTickX to set
     */
    public final void setUnitTickX(double unitTickX) {
        this.unitTickX = unitTickX;
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
    public CacheableData getData() {
        return cacheableData;
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