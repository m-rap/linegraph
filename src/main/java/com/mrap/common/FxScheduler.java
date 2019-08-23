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
package com.mrap.common;

import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;

/**
 *
 * @author software
 */
public class FxScheduler {

    private static class FxRunnable implements Runnable {
        private final Runnable r;
        public final int priority;
        public final long ts;
        
        public FxRunnable(Runnable r, int priority, long ts) {
            this.r = r;
            this.priority = priority;
            this.ts = ts;
        }
        
        @Override
        public void run() {
            r.run();
            instance.fxRunnableReady = true;
        }
    }
    
    public static int RUNNABLE_PRIORITY_HIGH = 0;
    public static int RUNNABLE_PRIORITY_LOW = 1;
    
    private FxRunnable toRun = null;
    private int currPrio = RUNNABLE_PRIORITY_HIGH;
    private boolean fxRunnableReady = true;
    private final ArrayDeque<FxRunnable>[] fxRunnables = new ArrayDeque[] {
        new ArrayDeque<>(),
        new ArrayDeque<>()
    };
    boolean prevIsEmpty;
    long emptyMs;
    
    AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (!fxRunnableReady) {
                return;
            }
            if (!render())
                timer.stop();
        }
    };
    
    private FxScheduler() {
        
    }
    
    private void start() {
        prevIsEmpty = true;
        emptyMs = System.currentTimeMillis();
        timer.start();
        //t.start();
    }
    
    private boolean isFrameEmpty() {
        return fxRunnables[0].isEmpty() && fxRunnables[1].isEmpty();
    }
    
    private Thread t = new Thread(() -> {
        boolean next = true;
        while (next) {
            if (!fxRunnableReady) {
                continue;
            }
            next = render();
        }
    });

    private boolean render() {
        synchronized (fxRunnables) {
            boolean isEmpty = isFrameEmpty();
            if (isEmpty != prevIsEmpty) {
                emptyMs = System.currentTimeMillis();
            } else {
                if (System.currentTimeMillis() - emptyMs > 3000) {
                    instance = null;
                    System.out.println("empty frames timeout");
                    return false;
                }
            }
            prevIsEmpty = isEmpty;
            
            toRun = null;
            for (currPrio = RUNNABLE_PRIORITY_HIGH; currPrio < fxRunnables.length; currPrio++) {
                ArrayDeque<FxRunnable> rs = fxRunnables[currPrio];
                if (rs.size() > 0) {
                    toRun = rs.pop();
                    if (currPrio == RUNNABLE_PRIORITY_LOW) {
                        long ts = System.currentTimeMillis();
                        if (ts - toRun.ts > 1000) {
                            System.out.println("Clearing old frames");
                            fxRunnables[currPrio].clear();
                        }
                    }
                    break;
                }
            }
        }
        if (toRun != null) {
            //if (toRun.priority == 0)
            fxRunnableReady = false;
            checkAndRunInternal(toRun);
        }
        return true;
    }
    
    private static FxScheduler instance = null;
    
    private void checkAndRunInternal(FxRunnable r) {
        try {
            if (!Platform.isFxApplicationThread())
                Platform.runLater(r);
            else
                r.run();
            Thread.sleep(1);
        } catch (InterruptedException ex) {
            Logger.getLogger(FxScheduler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void checkAndRun(Runnable r, int priority) {
        if (instance == null) {
            instance = new FxScheduler();
            //instance.t.start();
            instance.start();
        }
        synchronized (instance.fxRunnables) {
            instance.fxRunnables[priority].add(new FxRunnable(r, priority,
                    System.currentTimeMillis()));
        }
    }
    
    public static void checkAndRun(Runnable r) {
        checkAndRun(r, RUNNABLE_PRIORITY_HIGH);
    }
}
