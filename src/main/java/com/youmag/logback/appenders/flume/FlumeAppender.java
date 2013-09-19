/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package com.youmag.logback.appenders.flume;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import ch.qos.logback.core.status.ErrorStatus;

/**
 * An Appender that uses the Avro protocol to route events to Flume.
 */

public final class FlumeAppender extends AppenderBase<ILoggingEvent> {
    private ArrayList<FlumeAgent> agents = new ArrayList<FlumeAgent>();
    private AbstractFlumeManager manager = null;
    private String mdcIncludes = null;
    private String mdcExcludes = null;
    private String mdcRequired = null;
    private String eventPrefix = null;
    private String mdcPrefix = null;
    private boolean compressBody = false;
    private PatternLayout layout = null;
	private int batchSize = 1;
	private int reconnectDelay = 0;
	private int retries = 10;
	private String dataDir = null;
	private String type = "avro";
    private StatusLogger statusLogger;
    private String appName = null;
    private boolean includeCallerData = false;
    public static final int DEFAULT_QUEUE_SIZE = 256;
    private int queueSize = DEFAULT_QUEUE_SIZE;

    static final int UNDEFINED = -1;
    private int discardingThreshold = UNDEFINED;

    private String hostname = null;


    BlockingQueue<ILoggingEvent> blockingQueue;

    Worker worker = new Worker();


    /**
     * Create a Flume Avro Appender.
     */
    public FlumeAppender() {
    	super();
        statusLogger = new StatusLogger(getStatusManager(), getDeclaredOrigin());
    }

	/**
     * Publish the event.
     * @param event The ILoggingEvent.
     */
    public void append(final ILoggingEvent event) {
        if (isQueueBelowDiscardingThreshold()) {
            return;
        }
        preprocess(event);
        put(event);
    }


    private void put(ILoggingEvent event) {
        try {
            blockingQueue.put(event);
        } catch (InterruptedException e) {
            statusLogger.addError("Could not add event to queue", e);
        }
    }

    /**
     * Start this appender.
     */
    @Override
    public void start() {

        if (queueSize < 1) {
            statusLogger.addError("Invalid queue size [" + queueSize + "]");
            return;
        }
        blockingQueue = new ArrayBlockingQueue<ILoggingEvent>(queueSize);

        if (name == null) {
        	throw new RuntimeException("No name provided for Appender");
        }

        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            statusLogger.addWarn("Could not look up local hostname");
        }


        if (discardingThreshold == UNDEFINED)
            discardingThreshold = queueSize / 5;
        statusLogger.addInfo("Setting discardingThreshold to " + discardingThreshold);

        worker.setDaemon(true);
        worker.setName("FlumeAppender-Worker-" + worker.getName());

        super.start();
        worker.start();
    }

    /**
     * Stop this appender.
     */
    @Override
    public void stop() {
        super.stop();
        if (!isStarted())
            return;

        // mark this appender as stopped so that Worker can also stop if it is invoking aii.appendLoopOnAppenders
        // and sub-appenders consume the interruption
        super.stop();

        // interrupt the worker thread so that it can terminate. Note that the interruption can be consumed
        // by sub-appenders
        worker.interrupt();
        try {
            worker.join(1000);
        } catch (InterruptedException e) {
            addError("Failed to join worker thread", e);
        }
    }

    protected void preprocess(ILoggingEvent eventObject) {
        eventObject.prepareForDeferredProcessing();
        if(includeCallerData)
            eventObject.getCallerData();
    }

    private boolean isQueueBelowDiscardingThreshold() {
        return (blockingQueue.remainingCapacity() < discardingThreshold);
    }

    public boolean isIncludeCallerData() {
        return includeCallerData;
    }

    public void setIncludeCallerData(boolean includeCallerData) {
        this.includeCallerData = includeCallerData;
    }


    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getDiscardingThreshold() {
        return discardingThreshold;
    }

    public void setDiscardingThreshold(int discardingThreshold) {
        this.discardingThreshold = discardingThreshold;
    }

    public int getNumberOfElementsInQueue() {
        return blockingQueue.size();
    }


    public int getRemainingCapacity() {
        return blockingQueue.remainingCapacity();
    }



    public void addAgent(FlumeAgent agent) {
		this.agents.add(agent);
	}

	public void setManager(AbstractFlumeManager manager) {
		this.manager = manager;
	}

	public void setMdcIncludes(String mdcIncludes) {
		this.mdcIncludes = mdcIncludes;
	}

	public void setMdcExcludes(String mdcExcludes) {
		this.mdcExcludes = mdcExcludes;
	}

	public void setMdcRequired(String mdcRequired) {
		this.mdcRequired = mdcRequired;
	}

	public void setEventPrefix(String eventPrefix) {
		this.eventPrefix = eventPrefix;
	}

	public void setMdcPrefix(String mdcPrefix) {
		this.mdcPrefix = mdcPrefix;
	}

	public void setCompressBody(boolean compressBody) {
		this.compressBody = compressBody;
	}

	public void setLayout(PatternLayout layout) {
		this.layout = layout;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}

	public void setReconnectDelay(int reconnectDelay) {
		this.reconnectDelay = reconnectDelay;
	}

	public void setRetries(int retries) {
		this.retries = retries;
	}

	public void setDataDir(String dataDir) {
		this.dataDir = dataDir;
	}

	public void setType(String type) {
		this.type = type;
	}

    public void setAppName(String appName){
        this.appName = appName;
    }


	class Worker extends Thread {

        private AbstractFlumeManager manager = null;


        public void run() {
            FlumeAppender parent = FlumeAppender.this;

            AbstractFlumeManager manager = null;

            if (parent.agents == null || parent.agents.size() == 0) {
                addWarn("No agents provided, using defaults");
                FlumeAgent defaultAgent = new FlumeAgent("localhost", 4141);
                parent.agents.add(defaultAgent);
            }

            manager = new FlumeAvroManager(parent.name, parent.name, parent.agents, parent.batchSize,
                    parent.statusLogger);


            if (manager == null) {
                throw new RuntimeException("Could not build Flume manager, check your type");
            }
            addWarn("Using manager " + type);


            this.manager = manager;

            // loop while the parent is started
            while (parent.isStarted()) {
                try {
                    ILoggingEvent e = parent.blockingQueue.take();
                    this.append(e);
                } catch (RuntimeException re) {
                    statusLogger.addError("Could not append to flume", re);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        statusLogger.addWarn("couldnt sleep worker thread");
                    }
                } catch (InterruptedException ie) {
                    break;
                }
            }

            addInfo("Worker thread will flush remaining events before exiting. ");
            for (ILoggingEvent e : parent.blockingQueue) {
                this.append(e);
            }

        }

        private void append(final ILoggingEvent event) {
            FlumeAppender parent = FlumeAppender.this;

            final FlumeEvent flumeEvent =
                    new FlumeEvent(event, parent.mdcIncludes, parent.mdcExcludes,
                            parent.mdcRequired, parent.mdcPrefix,
                            parent.eventPrefix, parent.compressBody, parent.appName, parent.hostname);


            String str = flumeEvent.getEvent().getFormattedMessage();
            byte[] bytes = null;

            try {
                bytes = str.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            flumeEvent.setBody(bytes);
            this.manager.send(flumeEvent, parent.reconnectDelay, parent.retries);
        }
    }
}
