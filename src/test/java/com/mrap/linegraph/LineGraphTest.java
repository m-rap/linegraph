/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mrap.linegraph;

import com.mrap.common.RandomDataGenerator;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import static javafx.application.Application.launch;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
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
    
    int lineGraphCount = 2;
    LineGraph[] lineGraphs;
    RandomDataGenerator.RandomGenericGenerator randomGenerator = new RandomDataGenerator.RandomGenericGenerator(100);
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        randomGenerator.listeners.add((RandomDataGenerator.RandomGeneratorListener<float[]>) (float[] result) -> {
            for (LineGraph l : lineGraphs)
                l.addData(System.currentTimeMillis(), result[0], result[1], result[2]);
        });
        lineGraphs = new LineGraph[lineGraphCount];
        for (int i = 0; i < lineGraphCount; i++)
            lineGraphs[i] = new LineGraph(-5f, 5f, 1, 5, 100);
        Button saveBtn = new Button("Save");
        Button loadBtn = new Button("Load");
        Button startBtn = new Button("Start");
        Button startOnceBtn = new Button("Start Once");
        Button stopBtn = new Button("Stop");
        HBox hbox = new HBox(startBtn, startOnceBtn, stopBtn, saveBtn, loadBtn);
        VBox box = new VBox();
        for (LineGraph l : lineGraphs) {
            box.getChildren().add(l);
        }
        box.getChildren().add(hbox);
        AnchorPane pane = new AnchorPane(box);
        pane.setPrefWidth(600);
        pane.setPrefHeight(lineGraphCount * 200);
        AnchorPane.setTopAnchor(box, 0.0);
        AnchorPane.setRightAnchor(box, 0.0);
        AnchorPane.setBottomAnchor(box, 0.0);
        AnchorPane.setLeftAnchor(box, 0.0);
        
        saveBtn.setOnAction((ActionEvent event) -> {
            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy-HHmmss");
            Date date;
            if (lineGraphs[0].getStartMs() != -1) {
                date = new Date(lineGraphs[0].getStartMs());
            } else {
                date = new Date();
            }
        });
        
        startBtn.setOnAction((ActionEvent event) -> {
            try {
                randomGenerator.start();
            } catch (Exception ex) {
                Logger.getLogger(LineGraphTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        
        startOnceBtn.setOnAction((ActionEvent event) -> {
            long start = System.currentTimeMillis();
            int[] counter = new int[] {0};
            randomGenerator.nextMsRandoms(900000, (Object res) -> {
                long ts = (long)((double)counter[0]*randomGenerator.delay)+start;
                float[] result = (float[])res;
                //String[] str = new String[6];
                //for (int i = 0; i < 3; i++)
                //    str[i] = result[i] + "";
                //System.out.println(ts + " " + String.join(" ", str));
                for (LineGraph l : lineGraphs)
                    l.addData(ts, result[0], result[1], result[2]);
                counter[0]++;
            });
        });
        
        stopBtn.setOnAction((ActionEvent event) -> {
            randomGenerator.stop();
        });
        
        primaryStage.setScene(new Scene(pane));
        primaryStage.setOnCloseRequest((WindowEvent event) -> {
            randomGenerator.stop();
            for (LineGraph l : lineGraphs)
                l.runnable.stop();
        });
        primaryStage.show();
        for (LineGraph l : lineGraphs)
            l.runnable.start();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
