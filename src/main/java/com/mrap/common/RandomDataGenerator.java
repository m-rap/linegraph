/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mrap.common;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Random;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author m_rap
 * @param <T>
 */
public abstract class RandomDataGenerator<T> extends MRunnable {
    
    public interface RandomGeneratorListener<T> {
        void onNextRandom(T data);
    }
    
    public static class RandomGenericGenerator<T> extends RandomDataGenerator {

        T dataInstance;
        private Field[] fs;
        
        public RandomGenericGenerator(int freq, T dataInstance) {
            super(freq);
            this.dataInstance = dataInstance;
            if (!dataInstance.getClass().isArray()) {
                fs = dataInstance.getClass().getDeclaredFields();
                for (Field f : fs) {
                    if (f.getType().getName().equals("float")) {
                        f.setAccessible(true);
                    }
                }
            }
        }
        
        public RandomGenericGenerator(int freq) {
            this(freq, (T)new float[3]);
        }

        @Override
        protected T nextRandom() {
            if (dataInstance.getClass().isArray()) {
                int length = Array.getLength(dataInstance);
                for (int i = 0; i < length; i++) {
                    Object el = Array.get(dataInstance, i);
                    if (el instanceof Float) {
                        Array.set(dataInstance, i, nextFloatSeries(-100, 100, (float)el, 0.5f));
                    }
                }
                return dataInstance;
            }
            for (Field f : fs) {
                if (f.getType().getName().equals("float")) {
                    try {
                        f.set(dataInstance, nextFloatSeries(-100, 100, f.getFloat(dataInstance), 0.5f));
                    } catch (IllegalArgumentException ex) {
                        Logger.getLogger(RandomDataGenerator.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (IllegalAccessException ex) {
                        Logger.getLogger(RandomDataGenerator.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            return dataInstance;
        }
        
    }

    final Random r;
    public ArrayDeque<RandomGeneratorListener<T>> listeners = new ArrayDeque<>();
    public int delay;
    
    public RandomDataGenerator(int freq) {
        super(true);
        r = new Random();
        int f = freq > 0 ? freq : 1;
        delay = 1000/f - 1;
        if (delay < 0)
            delay = 0;
    }
    
    protected abstract T nextRandom();
    
    public void nextMsRandoms(long ms, Consumer<T> consumer) {
        long count = ms / (delay + 1);
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

    @Override
    public void onStart() throws Exception {
        
    }

    @Override
    public void onStop() {
        
    }

    @Override
    public void onRun() {
        for (RandomGeneratorListener<T> l : listeners)
            l.onNextRandom(nextRandom());
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Logger.getLogger(RandomDataGenerator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
