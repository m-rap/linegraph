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
import javafx.application.Platform;

/**
 *
 * @author software
 */
public class FxRunnableManager {
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
    public static int RUNNABLE_PRIORITY_MED = 1;
    public static int RUNNABLE_PRIORITY_LOW = 2;
    
    private FxRunnable toRun = null;
    private int currPrio = RUNNABLE_PRIORITY_HIGH;
    private boolean fxRunnableReady = true;
    private final ArrayDeque<FxRunnable>[] fxRunnables = new ArrayDeque[] {
        new ArrayDeque<>(),
        new ArrayDeque<>(),
        new ArrayDeque<>()
    };
    
    private Thread t = new Thread(() -> {
        while (fxRunnables != null) {
            synchronized (fxRunnables) {
                if (!fxRunnableReady) {
                    continue;
                }
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
        }
    });
    
    private static FxRunnableManager instance = null;
    
    private void checkAndRunInternal(FxRunnable r) {
        if (!Platform.isFxApplicationThread())
            Platform.runLater(r);
        else
            r.run();
    }
    
    public static void checkAndRun(Runnable r, int priority) {
        if (instance == null) {
            instance = new FxRunnableManager();
            instance.t.start();
        }
        synchronized (instance.fxRunnables) {
            instance.fxRunnables[priority].add(new FxRunnable(r, priority,
                    System.currentTimeMillis()));
        }
    }
    
    public static void checkAndRun(Runnable r) {
        checkAndRun(r, RUNNABLE_PRIORITY_MED);
    }
}
