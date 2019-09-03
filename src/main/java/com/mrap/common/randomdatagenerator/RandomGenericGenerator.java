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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sagan
 */
public class RandomGenericGenerator<T> extends RandomDataGenerator {
    T dataInstance;
    private Field[] fs;
    protected Object[][] minmax = null;
    final static Object[] DEFAULT_MINMAX = new Object[] {-100,100};

    public RandomGenericGenerator(int freq, T dataInstance, Object[][] minmax) {
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
        this.minmax = minmax;
    }
    
    public RandomGenericGenerator(int freq, Object[][] minmax) {
        this(freq, (T)new float[3], minmax);
    }
    
    public RandomGenericGenerator(int freq, T dataInstance) {
        this(freq, dataInstance, new Object[][] {DEFAULT_MINMAX});
    }

    public RandomGenericGenerator(int freq) {
        this(freq, new Object[][]{
            DEFAULT_MINMAX
        });
    }

    @Override
    protected T nextRandom() {
        float min = -100, max = 100;
        if (dataInstance.getClass().isArray()) {
            int length = Array.getLength(dataInstance);
            for (int i = 0, iMinmax = 0; i < length; i++) {
                Object el = Array.get(dataInstance, i);
                if (minmax != null && iMinmax < minmax.length) {
                    min = (float)minmax[iMinmax][0];
                    max = (float)minmax[iMinmax][1];
                    iMinmax++;
                }
                if (el instanceof Float) {
                    Array.set(dataInstance, i, nextFloatSeries(min, max, (float)el, 0.5f));
                }
            }
            return dataInstance;
        }
        min = -100;
        max = 100;
        for (int i = 0, iMinmax = 0; i < fs.length; i++) {
            Field f = fs[i];
            if (minmax != null && iMinmax < minmax.length) {
                min = (float)minmax[iMinmax][0];
                max = (float)minmax[iMinmax][1];
                iMinmax++;
            }
            if (f.getType().getName().equals("float")) {
                try {
                    f.set(dataInstance, nextFloatSeries(min, max, f.getFloat(dataInstance), 0.5f));
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
