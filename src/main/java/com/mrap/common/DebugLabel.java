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

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

/**
 *
 * @author Rian
 */
public class DebugLabel extends Label {
    AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (getParent() == null || !isVisible())
                return;
            StringBuilder sb = new StringBuilder("Log:");
            for (Object[] arr : trackedFields) {
                Object o = arr[0];
                Field f = (Field)arr[1];
                boolean tmpAccessible = f.isAccessible();
                f.setAccessible(true);
                String name = o.getClass().getName();
                String value = "";
                try {
                    value = f.get(o) + "";
                } catch (IllegalArgumentException | IllegalAccessException ex) {
                    Logger.getLogger(DebugLabel.class.getName()).log(Level.SEVERE, null, ex);
                }
                f.setAccessible(tmpAccessible);
                name = name.substring(name.lastIndexOf(".") + 1);
                sb.append("\n").append(name).append(".").
                        append(f.getName()).append(": ").append(value);
            }
            setText(sb.toString());
        }
    };
    
    public ArrayDeque<Object[]> trackedFields = new ArrayDeque<>();
    
    public DebugLabel() {
        super();
        timer.start();
        setMinHeight(Region.USE_PREF_SIZE);
        setPrefHeight(Region.USE_COMPUTED_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);
    }
    
    private Field searchField(Object o, String name) throws SecurityException {
        for (Class c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(name)) {
                    return f;
                }
            }
        }
        return null;
    }
    
    public boolean trackField(Object owner, String fieldName) {
        Field tmp = searchField(owner, fieldName);
        if (tmp == null)
            return false;
        trackedFields.add(new Object[] {owner, tmp});
        return true;
    }
}
