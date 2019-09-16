/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mrap.linegraph.examples;

import com.mrap.common.randomdatagenerator.*;
import com.mrap.common.*;
import com.mrap.data.CacheableData;
import com.mrap.linegraph.LineGraph;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 *
 * @author software
 */
public class LineGraphTest extends Application {
    
    int lineGraphCount = 1;
    long startMs = -1;
    LineGraph[] lineGraphs;
    Object[] aMinmax = new Object[] {-5f, 5f};
    RandomGenericGenerator randomGenerator1 = new RandomGenericGenerator(2000, 
            new Object[][] { aMinmax });
    RandomGenericGenerator randomGenerator2 = new RandomGenericGenerator(2000, 
            new Object[][] { aMinmax });
    DebugLabel debugLabel = new DebugLabel();
    
    NumberAxis xAxis = new NumberAxis();
    NumberAxis yAxis = new NumberAxis();
    LineChart lineChart = new LineChart(xAxis, yAxis);
    XYChart.Series[] seriess = new XYChart.Series[] {
        new XYChart.Series(), new XYChart.Series(), new XYChart.Series()
    };
    final ArrayDeque<Object[]> toAdd = new ArrayDeque<>();
    
//    final ArrayList<Object[]> data1 = new ArrayList<>();
//    final ArrayList<Object[]> data2 = new ArrayList<>();
    
    final CacheableData data1 = new CacheableData();
    final CacheableData data2 = new CacheableData();
    
    AnimationTimer lineChartTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            ArrayDeque<Object[]> tmp;
            synchronized(toAdd) {
                tmp = toAdd.clone();
                toAdd.clear();
            }
            boolean avail = false;
            double ts = 0;
            for (Object[] obj : tmp) {
                avail = true;
                ts = (double)((long)obj[0] - startMs)/1000;
                seriess[0].getData().add(new XYChart.Data(ts, (float)obj[1]));
                seriess[1].getData().add(new XYChart.Data(ts, (float)obj[2]));
                seriess[2].getData().add(new XYChart.Data(ts, (float)obj[3]));
            }
            if (avail) {
                xAxis.setUpperBound(ts + 1);
                xAxis.setLowerBound(ts + 1 - lineChart.getWidth() / 100);
            }
        }
    };
    boolean enableLineChart = false;
    boolean switched = false;
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        if (enableLineChart) {
            lineChart.setAnimated(false);
            lineChart.getData().addAll(Arrays.asList(seriess));
            xAxis.setAutoRanging(false);
            xAxis.setTickUnit(5);
            lineChartTimer.start();
        }
        
        randomGenerator1.listeners.add((RandomGeneratorListener<float[]>) (float[] result) -> {
            long ts = System.currentTimeMillis();
            synchronized (data1) {
                data1.add(new Object[] {ts, result[0], result[1], result[2]});
            }
            //for (LineGraph l : lineGraphs)
            //    l.addData(ts, result[0], result[1], result[2]);
            if (enableLineChart) {
                synchronized (toAdd) {
                    toAdd.push(new Object[] {ts, result[0], result[1], result[2]});
                }
            }
        });
        randomGenerator2.listeners.add((RandomGeneratorListener<float[]>) (float[] result) -> {
            long ts = System.currentTimeMillis();
            synchronized (data2) {
                data2.add(new Object[] {ts, result[0], result[1], result[2]});
            }
        });
        
        debugLabel.trackField(randomGenerator1, "fps");
        
        lineGraphs = new LineGraph[lineGraphCount];
        for (int i = 0; i < lineGraphCount; i++) {
            lineGraphs[i] = new LineGraph(-5f, 5f, 1, 1, 1, 300);
            debugLabel.trackField(lineGraphs[i].getData(), "size");
        }
        
        lineGraphs[0].setData(data1);
        //debugLabel.trackField(lineGraphs[0], "debugStr");
        
        Button saveBtn = new Button("Save");
        Button loadBtn = new Button("Load");
        Button startBtn = new Button("Start");
        Button startOnceBtn = new Button("Start Once");
        Button stopBtn = new Button("Stop");
        Button resetBtn = new Button("Reset");
        Button switchBtn = new Button("Switch Data");
        //HBox hbox = new HBox(startBtn, startOnceBtn, stopBtn, saveBtn, loadBtn);
        HBox hbox = new HBox(startBtn, stopBtn, resetBtn, switchBtn);
        hbox.setSpacing(5);
        VBox box = new VBox();
        box.setSpacing(5);
        box.setPadding(new Insets(10));
        box.getChildren().addAll(Arrays.asList(lineGraphs));
        if (enableLineChart)
            box.getChildren().add(lineChart);
        box.getChildren().add(hbox);
        box.getChildren().add(new HBox(debugLabel));
        AnchorPane pane = new AnchorPane(box);
        pane.setPrefWidth(800);
        pane.setPrefHeight((lineGraphCount + (enableLineChart ? 1 : 0)) * 400 + 70);
        AnchorPane.setTopAnchor(box, 0.0);
        AnchorPane.setRightAnchor(box, 0.0);
        AnchorPane.setBottomAnchor(box, 0.0);
        AnchorPane.setLeftAnchor(box, 0.0);
        
        saveBtn.setOnAction((ActionEvent event) -> {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy-HHmmss");
            Date date;
            if (lineGraphs[0].getData().getStartMs() != -1) {
                date = new Date(lineGraphs[0].getData().getStartMs());
            } else {
                date = new Date();
            }
        });
        
        startBtn.setOnAction((ActionEvent event) -> {
            try {
                randomGenerator1.start();
                randomGenerator2.start();
                startMs = System.currentTimeMillis();
            } catch (Exception ex) {
                Logger.getLogger(LineGraphTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        
        startOnceBtn.setOnAction((ActionEvent event) -> {
            long start = System.currentTimeMillis();
            int[] counter = new int[] {0};
            randomGenerator1.nextMsRandoms(900000, (Object res) -> {
                long ts = (long)((double)counter[0]*(randomGenerator1.freq))+start;
                float[] result = (float[])res;
                data1.add(new Object[] {ts, result[0], result[1], result[2]});
                //for (LineGraph l : lineGraphs)
                //    l.addData(ts, result[0], result[1], result[2]);
                counter[0]++;
            });
        });
        
        stopBtn.setOnAction((ActionEvent event) -> {
            randomGenerator1.stop();
            randomGenerator2.stop();
        });
        
        resetBtn.setOnAction((ActionEvent event) -> {
            data1.clear();
            data2.clear();
            //for (LineGraph l : lineGraphs)
            //    l.resetData();
        });
        
        switchBtn.setOnAction((ActionEvent event) -> {
            if (!switched) {
                switched = true;
                lineGraphs[0].setData(data2);
            } else {
                switched = false;
                lineGraphs[0].setData(data1);
            }
        });
        
        primaryStage.setTitle("LineGraph Demo");
        primaryStage.setScene(new Scene(pane));
        primaryStage.setOnCloseRequest((WindowEvent event) -> {
            randomGenerator1.stop();
            randomGenerator2.stop();
        });
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
