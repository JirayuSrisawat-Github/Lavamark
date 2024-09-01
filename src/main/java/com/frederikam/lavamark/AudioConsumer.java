/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.frederikam.lavamark;

import java.util.concurrent.atomic.AtomicInteger;

public class AudioConsumer extends Thread {

    private final Player player;

    private static final AtomicInteger served = new AtomicInteger();
    private static final AtomicInteger missed = new AtomicInteger();
    private static EndReason endReason = EndReason.NONE;
    private static boolean running = true;

    AudioConsumer(Player player) {
        this.player = player;
    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        long i = 0;

        while (running) {

            if (player.canProvide()) {
                served.incrementAndGet();
            } else {
                missed.incrementAndGet();
            }

            // A frame is 20ms
            int INTERVAL = 20;
            long targetTime = ((start / INTERVAL) + i + 1) * INTERVAL;
            long diff = targetTime - System.currentTimeMillis();
            i++;

            if(diff < -5000) {
                endReason = EndReason.CANT_KEEP_UP;
                break;
            }

            synchronized (this) {
                if(diff > 0) {
                    safeSleep(diff/2);
                }
            }
        }
    }

    private void safeSleep(Long ms) {
        long currentTimeMillis = System.currentTimeMillis();

        while (true) {
            if (System.currentTimeMillis() - currentTimeMillis >= ms) {
                break;
            }
        }
    }

    static Results getResults() {
        int serv = served.getAndSet(0);
        int miss = missed.getAndSet(0);

        if((serv + miss) / 100 < miss) {
            endReason = EndReason.MISSED_FRAMES;
            running = false;
        }

        return new Results(serv, miss, endReason);
    }

    public static class Results {
        int served;
        int missed;
        EndReason endReason;

        Results(int served, int missed, EndReason endReason) {
            this.served = served;
            this.missed = missed;
            this.endReason = endReason;
        }

        public int getServed() {
            return served;
        }

        public int getMissed() {
            return missed;
        }

        public EndReason getEndReason() {
            return endReason;
        }

        public double getLoss() {
            return (Math.floor((1 - ((double) served) / ((double) (served + missed))) * 10000) / 10000) * 100;
        }
    }

    public enum EndReason {
        NONE,
        CANT_KEEP_UP,
        MISSED_FRAMES
    }
}
