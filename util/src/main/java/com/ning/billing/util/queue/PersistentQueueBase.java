/* 
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.ning.billing.util.queue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class PersistentQueueBase implements QueueLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PersistentQueueBase.class);

    private final int nbThreads;
    private final Executor executor;
    private final String svcName;
    private final long sleepTimeMs;
    private final long waitTimeoutMs;

    private boolean isProcessingEvents;
    private int curActiveThreads;
    
    public PersistentQueueBase(final String svcName, final Executor executor, final int nbThreads, final long waitTimeoutMs, final long sleepTimeMs) {
        this.executor = executor;
        this.nbThreads = nbThreads;
        this.svcName = svcName;
        this.waitTimeoutMs = waitTimeoutMs;
        this.sleepTimeMs = sleepTimeMs;
        this.isProcessingEvents = false;
        this.curActiveThreads = 0;
    }
    
    @Override
    public void startQueue() {
        
        isProcessingEvents = true;
        curActiveThreads = 0;
        
        final PersistentQueueBase thePersistentQ = this;
        final CountDownLatch doneInitialization = new CountDownLatch(nbThreads);

        log.info(String.format("%s: Starting with %d threads",
                svcName, nbThreads));
        
        for (int i = 0; i < nbThreads; i++) {
            executor.execute(new Runnable() {
                @Override
                public void run() {

                    log.info(String.format("%s: Thread %s [%d] starting",
                            svcName,
                            Thread.currentThread().getName(),
                            Thread.currentThread().getId()));
                    
                    synchronized(thePersistentQ) {
                        curActiveThreads++;
                    }

                    doneInitialization.countDown();
                    
                    try {
                        while (true) {
                            
                            synchronized(thePersistentQ) {
                                if (!isProcessingEvents) {
                                    thePersistentQ.notify();
                                    break;
                                }
                            }

                            try {
                                doProcessEvents();
                            } catch (Exception e) {
                                log.warn(String.format("%s: Thread  %s  [%d] got an exception, catching and moving on...",
                                        svcName,
                                        Thread.currentThread().getName(),
                                        Thread.currentThread().getId()), e);
                            }
                            sleepALittle();
                        }
                    } catch (InterruptedException e) {
                        log.info(String.format("%s: Thread %s got interrupted, exting... ", svcName, Thread.currentThread().getName()));
                    } catch (Throwable e) {
                        log.error(String.format("%s: Thread %s got an exception, exting... ", svcName, Thread.currentThread().getName()), e);                        
                    } finally {

                        log.info(String.format("%s: Thread %s has exited", svcName, Thread.currentThread().getName()));                                                
                        synchronized(thePersistentQ) {
                            curActiveThreads--;
                        }
                    }
                }
                
                private void sleepALittle() throws InterruptedException {
                    Thread.sleep(sleepTimeMs);
                }
            });
        }
        try {
            boolean success = doneInitialization.await(sleepTimeMs, TimeUnit.MILLISECONDS);
            if (!success) {
                
                log.warn(String.format("%s: Failed to wait for all threads to be started, got %d/%d", svcName, (nbThreads - doneInitialization.getCount()), nbThreads));
            } else {
                log.info(String.format("%s: Done waiting for all threads to be started, got %d/%d", svcName, (nbThreads - doneInitialization.getCount()), nbThreads));                
            }
        } catch (InterruptedException e) {
            log.warn(String.format("%s: Start sequence, got interrupted", svcName));
        }
    }
    
    
    @Override
    public void stopQueue() {
        int remaining = 0;
        try {
            synchronized(this) {
                isProcessingEvents = false;
                long ini = System.currentTimeMillis();
                long remainingWaitTimeMs = waitTimeoutMs;
                while (curActiveThreads > 0 && remainingWaitTimeMs > 0) {
                    wait(1000);
                    remainingWaitTimeMs = waitTimeoutMs - (System.currentTimeMillis() - ini);
                }
                remaining = curActiveThreads;
            }
            
        } catch (InterruptedException ignore) {
            log.info(String.format("%s: Stop sequence has been interrupted, remaining active threads = %d", svcName, curActiveThreads));
        } finally {
            if (remaining > 0) {
                log.error(String.format("%s: Stop sequence completed with %d active remaing threads", svcName, curActiveThreads));
            } else {
                log.info(String.format("%s: Stop sequence completed with %d active remaing threads", svcName, curActiveThreads));                
            }
            curActiveThreads = 0;
        }
    }
    
    
    @Override
    public abstract int doProcessEvents();
}
