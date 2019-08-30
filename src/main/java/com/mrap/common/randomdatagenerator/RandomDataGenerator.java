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
package com.mrap.common.randomdatagenerator;

import java.util.ArrayDeque;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author m_rap
 * @param <T>
 */
public abstract class RandomDataGenerator<T> {
    
    public final int freq;
    final Random r;
    public ArrayDeque<RandomGeneratorListener<T>> listeners = new ArrayDeque<>();
    ScheduledExecutorService executor = null;
    int fps = 0;
    int frames = 0;
    long prevSec = 0;
    
    Runnable runnable = () -> {
        for (RandomGeneratorListener<T> l : listeners)
            l.onNextRandom(nextRandom());
        frames++;
        long now = System.currentTimeMillis();
        long currSec = now / 1000;
        if (currSec > prevSec) {
            prevSec = currSec;
            fps = frames;
            frames = 0;
        }
    };
    
    public RandomDataGenerator(int freq0) {
        freq = freq0;
        r = new Random();
    }
    
    protected abstract T nextRandom();
    
    public void nextMsRandoms(long ms, Consumer<T> consumer) {
        long count = (long)freq;
        for (int i = 0; i < count; i++) {
            consumer.accept(nextRandom());
        }
    }
    
    protected float nextFloatSeries(float min, float max, float prev, float range) {
        float rangeBottom;
        float rangeTop;
        float diff1 = Math.abs(prev - min);
        float diff2 = Math.abs(prev - max);
        if (diff1 < diff2) {
            if (diff1 < range / 2) {
                rangeBottom = min;
            } else {
                rangeBottom = prev - range / 2;
            }
        } else {
            if (diff2 < range / 2) {
                rangeTop = max;
            } else {
                rangeTop = prev + range / 2;
            }
            rangeBottom = rangeTop - range;
        }
        return rangeBottom + r.nextFloat() * range;
    }
    
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(runnable, 0, (long)((1000.0 / freq) * 1000), TimeUnit.MICROSECONDS);
    }
    
    public void stop() {
        try {
            if (executor == null)
                return;
            fps = 0;
            frames = 0;
            executor.shutdown();
            executor.awaitTermination(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(RandomDataGenerator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
