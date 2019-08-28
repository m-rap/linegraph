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
package com.mrap.common.deprecated;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 *
 * @author software
 */
public class FxScheduler extends BaseService {

    private static class FxRunnable implements Runnable {
        private final Runnable r;
        public final long ts;
        
        public FxRunnable(Runnable r, long ts) {
            this.r = r;
            this.ts = ts;
        }
        
        @Override
        public void run() {
            r.run();
            instance.frames++;
            synchronized (instance.waiter) {
                instance.waiter.notifyAll();
            }
        }
    }
    
    public static int RUNNABLE_PRIORITY_HIGH = 0;
    public static int RUNNABLE_PRIORITY_LOW = 1;
    
    private FxRunnable toRun = null;
    private int currPrio = RUNNABLE_PRIORITY_HIGH;
    private final ArrayDeque<FxRunnable>[] fxRunnables = new ArrayDeque[] {
        new ArrayDeque<>(),
        new ArrayDeque<>()
    };
    private boolean prevIsEmpty = true;
    private long emptyMs = System.currentTimeMillis();
    
    private final Object waiter = new Object();
    
    String debugStr = "";
    
    private final static FxScheduler instance = new FxScheduler();
    
    private FxScheduler() {
        super(true);
        overrideFrameCount = true;
    }
    
    private boolean isFrameEmpty() {
        return fxRunnables[0].isEmpty() && fxRunnables[1].isEmpty();
    }

    private boolean render() {
        long ts = System.currentTimeMillis();
        boolean isEmpty = isFrameEmpty();
        if (isEmpty) {
            if (!prevIsEmpty) {
                emptyMs = System.currentTimeMillis();
            } else {
                if (System.currentTimeMillis() - emptyMs > 500) {
                    prevIsEmpty = true;
                    stop();
                    System.out.println("empty frames timeout");
                    return false;
                }
            }
        }
        prevIsEmpty = isEmpty;
        toRun = null;
        
        synchronized (fxRunnables) {
            for (currPrio = RUNNABLE_PRIORITY_HIGH; currPrio < fxRunnables.length; currPrio++) {
                ArrayDeque<FxRunnable> rs = fxRunnables[currPrio];
                if (rs.size() > 0) {
                    toRun = rs.pop();
                    if (currPrio == RUNNABLE_PRIORITY_LOW) {
                        if (ts - toRun.ts > 1000) {
                            System.out.println("Clearing old frames " + fxRunnables[currPrio].size());
                            fxRunnables[currPrio].clear();
                        }
                    }
                    break;
                }
            }
        }
        if (toRun != null) {
            checkAndRunInternal(toRun);
        }
        return true;
    }
    
    private void checkAndRunInternal(FxRunnable r) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(r);
            try {
                synchronized (waiter) {
                    waiter.wait(100);
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(FxScheduler.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            r.run();
        }
    }
    
    @Override
    public void onStart() throws Exception {
        
    }

    @Override
    public void onStop() {
        
    }

    @Override
    public void onRun() {
        render();
    }
    
    public static FxScheduler instance() {
        return instance;
    }
    
    public static void checkAndRun(Runnable r, int priority) {
        try {
            instance.start();
            synchronized (instance.fxRunnables) {
                instance.fxRunnables[priority].add(new FxRunnable(r, System.currentTimeMillis()));
            }
        } catch (Exception ex) {
            Logger.getLogger(FxScheduler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void checkAndRun(Runnable r) {
        checkAndRun(r, RUNNABLE_PRIORITY_HIGH);
    }
}
