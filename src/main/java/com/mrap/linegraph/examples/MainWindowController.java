/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mrap.linegraph.examples;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;

/**
 * FXML Controller class
 *
 * @author Rian
 */
public class MainWindowController implements Initializable {

    final static long ANIMATION_TIMEOUT = 1000;
    final static int FPS = 30;
    
    @FXML
    AnchorPane mixerPane;
    @FXML
    Canvas canvas;
    
    GraphicsContext gc;
    double pixPerMs;
    int unitTick = 1000;
    
    long startTs;
    
    long animationTime = ANIMATION_TIMEOUT;
    long animationStart;
    long delay;
    
    private double minPixPerMs;
    double mouseX = 0, mouseY = 0;
    AnimationTimer animTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (animationTime >= ANIMATION_TIMEOUT) {
                animationTime = ANIMATION_TIMEOUT;
                animTimer.stop();
                return;
            }
            draw();
            animationTime = System.currentTimeMillis() - animationStart;
        }
    };
    
    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        startTs = 0; //System.currentTimeMillis();
        
        gc = canvas.getGraphicsContext2D();
        canvas.setOnScroll((ScrollEvent event) -> {
            double x = event.getX();
            long midTs = (long)(x / pixPerMs) + startTs;
            pixPerMs += event.getDeltaY() * 0.0001;
            if (pixPerMs <= minPixPerMs)
                pixPerMs = minPixPerMs;
            startTs = midTs - (long)(x / pixPerMs);
            startAnimation();
        });
        canvas.setOnMouseMoved((MouseEvent event) -> {
            mouseX = event.getX();
            mouseY = event.getY();
            startAnimation();
        });
        delay = 1000 / FPS;
        minPixPerMs = canvas.getHeight() / (60 * 1000);
        pixPerMs = minPixPerMs;
        startAnimation();
    }
    
    void startAnimation() {
        animationTime = 0;
        animationStart = System.currentTimeMillis();
        animTimer.start();
    }
    
    void draw() {
        double width = canvas.getWidth(),
               height = canvas.getHeight();
        gc.clearRect(0, 0, width, height);
        double unitTickLength = unitTick * pixPerMs;
        
        int upperUnitTick, lowerUnitTick;
        int digitNum = (int)Math.floor(Math.log10(unitTick));
        int base = (int)Math.pow(10, digitNum);
        if (unitTick == base) {
            upperUnitTick = (int)(5 * Math.pow(10, digitNum));
            lowerUnitTick = unitTick / 2;
        } else {
            upperUnitTick = unitTick * 2;
            lowerUnitTick = (int)Math.pow(10, digitNum);
        }
        
        if (unitTickLength < 50.0) {
            unitTick = upperUnitTick;
        } else if (unitTickLength >= 100.0) {
            if (lowerUnitTick * pixPerMs >= 50.0) {
                unitTick = lowerUnitTick;
            }
        }
        
        gc.setStroke(new Color(0, 0, 0, 1));
        long i = startTs / unitTick;
        long mod = startTs % unitTick;
        if (mod > unitTick / 2) {
            i++;
        }
        i *= unitTick;
        
        double fontSize = gc.getFont().getSize();
        for (double x =  pixPerMs * (i - startTs); x < width; i += unitTick) {
            gc.beginPath();
            x =  pixPerMs * (i - startTs);
            gc.moveTo(x, 0);
            gc.lineTo(x, height);
            gc.stroke();
            gc.closePath();
            
            gc.strokeText("" + i, x, fontSize);
        }
        gc.strokeText(String.format("%.4f\n%d\n%d\n%.4f", mouseX, 
                (long)((mouseX / pixPerMs) + startTs), startTs, pixPerMs),
                5, height - 50);
    }
}
