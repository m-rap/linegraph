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
package com.mrap.data;

import com.mrap.linegraph.LineGraph;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Rian
 */
public class CacheableData extends ArrayList<Object[]> {
    
    public static interface ChangeListener {
        public void onAdd();
        public void onReset();
    }
    
    final CacheableData that = this;
    
    private static final int CLEAR_DATA_MS = 5 * 60 * 1000; // 5 menit
    private long DUMP_INTERVAL_MS = 60 * 1000;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyyMMdd_HHmmss");
    
    private boolean isSaving = false;
    private boolean enableDump = true;
    private int dumpStartIdx = 0;
    boolean isDumping = false;
    private int dumpEndIdx = -1;
    private final Object fileDumpLock = new Object();
    File dir = new File(".linegraph");
    protected String name = "";
    private int fieldCount = 0;
    int dumpedSize = 0;
    private int dumpedEndIdx = -1;
    protected long startMs = -1;
    
    private Object hasher = new Object();
    
    static ThreadFactory dumpThreadFactory = (Runnable r) -> new Thread(r, "dumper");
    static ThreadFactory terminatorThreadFactory = (Runnable r) -> new Thread(r, "terminator");
    
    private ScheduledExecutorService dumpExecutor = Executors.newSingleThreadScheduledExecutor(dumpThreadFactory);
    private ScheduledExecutorService executorTerminator = Executors.newSingleThreadScheduledExecutor(terminatorThreadFactory);
    private ScheduledFuture<?> dumpScheduledFuture = null;
    private ScheduledFuture<?> terminatorSceduledFuture = null;
    
    private Runnable dumpRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isEnableDump())
                return;
            if (isEmpty()) {
                return;
            }
            if (dumpStartIdx >= size() || dumpStartIdx < 0) {
                return;
            }
            
            isDumping = true;
            
            ArrayDeque<Object[]> temp;
            synchronized (that) {
                long a = (long)that.get(0)[0], b = (long)that.get(that.size() - 1)[0];
                log("start dumping from " + (long)that.get(dumpStartIdx)[0] + 
                        ", current data size,start,end,length " + that.size() + "," + a +
                        "," + b + "," + (b - a));
                dumpEndIdx = that.size() - 1;
                temp = new ArrayDeque<>();
                for (; dumpStartIdx <= dumpEndIdx; dumpStartIdx++) {
                    temp.add(that.get(dumpStartIdx));
                }
            }

            if (!temp.isEmpty()) {
                synchronized (fileDumpLock) {
                    try {
                        if (!dir.exists()) {
                            Files.createDirectory(dir.toPath());
                        }

                        File file = new File(getCachePath());
                        boolean firstCreate = !file.exists();
                        FileOutputStream fos = new FileOutputStream(file, true);
                        ByteBuffer buff = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                        if (firstCreate) {
                            buff.putInt(fieldCount);
                            fos.write(buff.array());
                        }
                        buff = ByteBuffer.allocate(8 + 4 * fieldCount).order(ByteOrder.LITTLE_ENDIAN);
                        for (Object[] datum : temp) {
                            buff.position(0);
                            buff.putLong((long) datum[0]);
                            for (int i = 0; i < fieldCount; i++)
                                buff.putFloat((float) datum[i + 1]);
                            fos.write(buff.array());
                        }
                        fos.flush();
                        fos.close();
                        dumpedSize += temp.size();
                        dumpedEndIdx = dumpEndIdx;

                        log(dumpedSize + " dumped, " +
                            ((float) ((long) temp.getLast()[0] - dumpStartIdx) / 60000) +
                            " mins length");

                    } catch (IOException ex) {
                        Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            isDumping = false;
        }
    };
    
    ChangeListener internalListener = new ChangeListener() {
        @Override
        public void onAdd() {
            Object[] datum = get(size() - 1);
            if (startMs == -1) {
                startMs = (long) datum[0];
            }
            
            if (fieldCount == 0)
                fieldCount = datum.length - 1;

            long timeLength, a = 0, b = 0;
            try {
                a = (long)get(0)[0];
                timeLength = (long) datum[0] - a;
                b = isEnableDump() ? (long)get(dumpStartIdx)[0] : 0;
                if (timeLength >= CLEAR_DATA_MS * 2 && (!isEnableDump() || timeLength / 2 < b - a)) {
                    int dataToClearCount = size() / 2;
                    subList(0, dataToClearCount).clear();

                    if (isEnableDump())
                        dumpStartIdx -= dataToClearCount;

                    b = (long)get(size() - 1)[0];
                    log("old data dropped, current size,start,end,length " + size() + "," + a +
                        "," + b + "," + (b - a));
                }
            } catch (Exception e) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(4096);
                PrintWriter pw = new PrintWriter(bos);
                e.printStackTrace(pw);
                log(bos.toString() + "\n" + a + " " + b + " " + dumpStartIdx);
            }

            if (dumpScheduledFuture == null || dumpScheduledFuture.isDone()) {
                dumpScheduledFuture = dumpExecutor.scheduleAtFixedRate(dumpRunnable,
                        DUMP_INTERVAL_MS, DUMP_INTERVAL_MS, TimeUnit.MILLISECONDS);
            }

            if (terminatorSceduledFuture != null && !terminatorSceduledFuture.isDone()) {
                terminatorSceduledFuture.cancel(true);
            }

            terminatorSceduledFuture = executorTerminator.schedule(() -> {
                dumpExecutor.shutdown();
                try {
                    dumpExecutor.awaitTermination(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                }
                dumpExecutor = Executors.newSingleThreadScheduledExecutor(dumpThreadFactory);
                dumpRunnable.run();
                executorTerminator.shutdown();
                executorTerminator = Executors.newSingleThreadScheduledExecutor(terminatorThreadFactory);
            }, 1700, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onReset() {
            resetIndices();
        }
    };
    
    ArrayDeque<ChangeListener> listeners = new ArrayDeque<>();
    
    public CacheableData() {
        super(new ArrayList<Object[]>());
        addListener(internalListener);
    }
    
    public final void addListener(ChangeListener l) {
        listeners.add(l);
    }
    
    public void removeListener(ChangeListener l) {
        listeners.remove(l);
    }
    
    @Override
    public boolean add(Object[] d) {
        boolean res = super.add(d);
        synchronized (this) {
            for (ChangeListener l : listeners)
                l.onAdd();
        }
        return res;
    }
    
    @Override
    public void clear() {
        super.clear();
        synchronized (this) {
            for (ChangeListener l : listeners)
                l.onReset();
        }
    }
    
    public void log(String msg) {
        try {
            File dumpLogFile = new File(getCachePathNoExt() + ".log");
            if (!dir.exists()) {
                Files.createDirectory(dir.toPath());
            }
            
            FileOutputStream dumpLogFos = new FileOutputStream(dumpLogFile, true);
            PrintWriter pw = new PrintWriter(dumpLogFos);
            
            pw.println(msg);
            pw.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public String getCachePath() {
        return getCachePathNoExt() + ".bin";
    }
    
    public String getCachePathNoExt() {
        return ".linegraph/" + getFinalName() + "_" + SDF.format(new Date(startMs));
    }
    
    public String getFinalName() {
        return (name != null && !name.isEmpty()) ? name : "" + (long)(hasher.hashCode() & 0xFFFFFFFFL);
    }
    
    /**
     * @return the name
     */
    public String getName() {
        return name;
    }
    
    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
        //cbAutoscale.setSelected(PropertiesLoader.getDefault().getProperty("linegraph_" + name + "_autoscale", "false").equals("true"));
        //enableDump = PropertiesLoader.getDefault().getProperty("linegraph_" + name + "_enabledump", "true").equals("true");
    }
    
    /**
     * @return the fieldCount
     */
    public int getFieldCount() {
        return fieldCount;
    }

    /**
     * @param fieldCount the fieldCount to set
     */
    public final void setFieldCount(int fieldCount) {
        synchronized (this) {
            this.fieldCount = fieldCount;
        }
    }
    
    public void saveData(final long from, final long to, final Consumer<Object[]> dataConsumer,
            final Consumer<Object> progressConsumer, final String path, final String... headers) {
        if (isSaving)
            return;
        Thread t = new Thread(() -> {
            isSaving = true;
            try {
                File f = new File(path);
                if (f.exists()) {
                    throw new Exception("Saving failed: File exists");
                }
                FileWriter fw = new FileWriter(f);
                if (headers != null) {
                    for (int index = 0; index < headers.length; index++) {
                        if (index != 0) {
                            fw.write(",");
                        }
                        fw.write(headers[index]);
                    }
                    fw.write("\r\n");
                }
                
                ArrayList<Object[]> tempData = new ArrayList<>();
                long total;
                int counter = 0;
                synchronized (that) {
                    tempData.addAll(subList(dumpedEndIdx + 1, size()));
                }
                synchronized (fileDumpLock) {
                    total = tempData.size();
                    ArrayList<Path> list = new ArrayList<>();
                    File cacheFile = new File(getCachePath());
                    if (cacheFile.exists() && cacheFile.canRead()) {
                        final FileWriter fw1 = fw;
                        final int counter0 = counter;
                        final long total0 = total;
                        long cacheCount = loadCacheData(from, to, (Object[] tmp) -> {
                            try {
                                int counter1 = (int)tmp[0];
                                long total1 = (long)tmp[1];
                                Object[] datum = (Object[])tmp[2];

                                long timestamp = (long)datum[0];
                                fw1.write((long)datum[0] + ",");
                                for (int i = 1; i < datum.length; i++)
                                    fw1.write((float)datum[i] + ",");

                                if (dataConsumer != null)
                                    dataConsumer.accept(datum);

                                if (progressConsumer != null)
                                    progressConsumer.accept((double) (counter1 + counter0) / (total0 + total1));

                            } catch (IOException ex) {
                                Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });
                        total += cacheCount;
                        counter += cacheCount;
                    }
                }
                
                int i = 0;
                for (Object[] row : tempData) {
                    long timestamp = (long) row[0];
                    if (to != -1 && timestamp > to) {
                        total -= (tempData.size() - i);
                        break;
                    }
                    if (from != -1 && timestamp < from) {
                        total--;
                        continue;
                    }
                    
                    fw.write(row[0].toString() + ",");
                    for (int j = 1; j < row.length; j++)
                        fw.write(row[j].toString() + ",");
                    
                    if (dataConsumer != null) {
                        dataConsumer.accept(row);
                    }
                    
                    counter++;
                    if (total <= 0)
                        total = 1;
                    if (progressConsumer != null && counter < total) {
                        progressConsumer.accept((double) counter / total);
                    }
                    i++;
                }
                fw.close();
                
                if (progressConsumer != null) {
                    progressConsumer.accept((double) 1);
                }
            } catch (Exception ex) {
                Logger.getLogger(LineGraph.class.getName()).log(Level.SEVERE, null, ex);
                if (progressConsumer != null) {
                    progressConsumer.accept(ex);
                }
            }
            isSaving = false;
        });
        t.start();
    }
    
    private void resetIndices() {
        startMs = -1;
        dumpedSize = 0;
        dumpedEndIdx = -1;
        dumpStartIdx = 0;
        dumpEndIdx = -1;
        hasher = new Object();
    }
    
    public long getStartMs() {
        return startMs;
    }
    
    public long loadCacheData(long from, long to, Consumer<Object[]> consumer) throws IOException {
        FileInputStream fis;
        long total;
        synchronized (fileDumpLock) {
            File f = new File(getCachePath());
            if (!f.exists() || !f.canRead())
                return 0;
            
            fis = new FileInputStream(f);
            ByteBuffer buff = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            int n = buff.getInt();
            int chunkSize = 8 + 4 * n;
            total = f.length() / chunkSize;
            buff = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN);
            int remaining;
            int count = 0;
            while ((remaining = fis.available()) > 0) {
                if (remaining < chunkSize) {
                    break;
                }
                fis.read(buff.array());
                buff.position(0);
                Object[] tmp = new Object[n + 1];
                long timestamp = buff.getLong();
                
                if (to != -1 && timestamp > to) {
                    total -= ((fis.available() / chunkSize) - 1);
                    break;
                }
                if (from != -1 && timestamp < from) {
                    total--;
                    continue;
                }
                
                tmp[0] = timestamp;
                for (int i = 0; i < n; i++)
                    tmp[i + 1] = buff.getFloat();
                
                count++;
                if (total <= 0)
                    total = 1;
                
                consumer.accept(new Object[] {count, total, tmp});
            }
            fis.close();
        }
        return total;
    }
    
    public ArrayList<Object[]> loadCacheData(long from, long to) throws IOException {
        ArrayList<Object[]> cacheData = new ArrayList<>();
        loadCacheData(from, to, (Object[] datum) -> {
            cacheData.add(datum);
        });
        return cacheData;
    }
    
    public void loadCsv(String path, boolean useHeader, int[] totalLine) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(path));
        if (useHeader) {
            sc.nextLine();
        }
        
        synchronized (this) {
            while (sc.hasNextLine()) {
                try {
                    String[] valStr = sc.nextLine().split(",");
                    Object[] d = new Object[]{
                        Long.parseLong(valStr[0]),
                        Float.parseFloat(valStr[1]),
                        Float.parseFloat(valStr[2]),
                        Float.parseFloat(valStr[3]),};
                    add(d);
                } catch (NumberFormatException ex) {
                    totalLine[1]++;
                }
                totalLine[0]++;
            }
        }
    }

    /**
     * @return the enableDump
     */
    public boolean isEnableDump() {
        return enableDump;
    }

    /**
     * @param enableDump the enableDump to set
     */
    public void setEnableDump(boolean enableDump) {
        this.enableDump = enableDump;
    }
}
