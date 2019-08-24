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

import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author software
 */
public abstract class BaseService implements Runnable {
    
    static final long NONREALTIME_DELAY = 1000/30;
    
    protected boolean running = false;
    protected Thread t = null;
    private long prevSec = 0;
    protected int fps = 0;
    protected int frames = 0;
    
    public abstract void onStart() throws Exception;
    public abstract void onStop();
    public abstract void onRun();
    private final boolean realTime;
    private long delay = NONREALTIME_DELAY;
    
    public BaseService(boolean realTime) {
        this.realTime = realTime;
    }
    
    public BaseService(long delay) {
        this.realTime = false;
        this.delay = delay;
    }
    
    public BaseService() {
        this(false);
    }
    
    public String getName() {
        return getClass().getSimpleName();
    }
    
    public void start() throws Exception {
        if (running)
            return;
        
        try {
            running = true;
            t = new Thread(this, getName());
            onStart();
            t.start();
        } catch (Exception ex) {
            running = false;
            t = null;
            throw ex;
        }
    }
    
    public void join() {
        if (t == null) {
            return;
        }
        try {
            t.join();
        } catch (InterruptedException ex) {
            Logger.getLogger(BaseService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void stop() {
        if (!running)
            return;
        
        running = false;
        onStop();
        join();
        t = null;
    }
    
    @Override
    public void run() {
        while (running) {
            long now = System.currentTimeMillis();
            onRun();
            frames++;
            if (now - prevSec > 1000) {
                prevSec = now;
                fps = frames;
                frames = 0;
            }
            try {
                if (realTime) {
                    //Thread.sleep(1);
                    LockSupport.parkNanos(1);
                } else {
                    Thread.sleep(delay);
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(BaseService.class.getName()).log(Level.INFO, null, ex);
            }
        }
    }
}
