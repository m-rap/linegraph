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
    
    static final int DEFAULT_TARGETFPS = 60;
    
    protected boolean running = false;
    protected Thread t = null;
    protected long prevSec = 0;
    protected int framesPerSecond = 0;
    protected int frames = 0;
    public double targetFps = DEFAULT_TARGETFPS;
    
    public abstract void onStart() throws Exception;
    public abstract void onStop();
    public abstract void onRun();
    private double delay;
    
    public BaseService(boolean realTime) {
        this(realTime ? 0.0 : DEFAULT_TARGETFPS);
    }
    
    public BaseService(double fps) {
        targetFps = fps;
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
            now = System.currentTimeMillis();
            if (now - prevSec > 1000) {
                prevSec = now;
                framesPerSecond = frames;
                frames = 0;
            }
            if (targetFps == 0.0) {
                //Thread.sleep(1);
                LockSupport.parkNanos(1);
            } else {
                //Thread.sleep(delay);
                double remainTargetFrames = targetFps - frames;
                double remainMs = (prevSec + 1000) - now;
                if (remainTargetFrames == 0 || remainMs <= 0) {
                    LockSupport.parkNanos(1);
                } else {
                    if (remainTargetFrames > 0) {
                        delay = remainMs / remainTargetFrames;
                    } else if (remainTargetFrames < 0) {
                        delay = now - prevSec;
                    }
                    LockSupport.parkNanos((long)(delay * 1000000));
                }
            }
        }
    }
}
