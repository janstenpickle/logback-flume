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

import org.apache.avro.AvroRemoteException;
import org.apache.avro.ipc.NettyTransceiver;
import org.apache.avro.ipc.Transceiver;
import org.apache.avro.ipc.specific.SpecificRequestor;
import org.apache.flume.source.avro.AvroFlumeEvent;
import org.apache.flume.source.avro.AvroSourceProtocol;
import org.apache.flume.source.avro.Status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manager for FlumeAvroAppenders.
 */
public class FlumeAvroManager extends AbstractFlumeManager {

    /**
      The default reconnection delay (500 milliseconds or .5 seconds).
     */
    public static final int DEFAULT_RECONNECTION_DELAY   = 500;

    private static final int DEFAULT_RECONNECTS = 3;

    private AvroSourceProtocol client;

    private final List<FlumeAgent> agents;

    private final int batchSize;

    private final EventList events = new EventList();

    private int current = 0;

    private Transceiver transceiver;

    private StatusLogger statusLogger;

    /**
     * Constructor
     * @param name The unique name of this manager.
     * @param agents An array of Agents.
     * @param batchSize The number of events to include in a batch.
     */
    protected FlumeAvroManager(final String name, final String shortName, final List<FlumeAgent> agents, final int batchSize, StatusLogger statusLogger) {
        super(name);
        this.statusLogger = statusLogger;
    	this.agents = agents;
        this.batchSize = batchSize;
        this.client = connect(agents);
    }


    /**
     * Returns the index of the current agent.
     * @return The index for the current agent.
     */
    public int getCurrent() {
        return current;
    }

    @Override
    public synchronized void send(final FlumeEvent event, int delay, int retries)  {
        if (delay == 0) {
            delay = DEFAULT_RECONNECTION_DELAY;
        }
        if (retries == 0) {
            retries = DEFAULT_RECONNECTS;
        }
        if (client == null) {
            int attempts = 0;
            while (client == null && attempts < retries) {
                client = connect(agents);
                 statusLogger.addWarn("Could not connect to agent, retrying in "+delay+"ms");
                sleep(delay);
                ++attempts;
            }
        }
        String msg = "No Flume agents are available";
        if (client != null) {
            final AvroFlumeEvent avroEvent = new AvroFlumeEvent();
            avroEvent.setBody(ByteBuffer.wrap(event.getBody()));
            avroEvent.setHeaders(new HashMap<CharSequence, CharSequence>());

            for (final Map.Entry<String, String> entry : event.getHeaders().entrySet()) {
                avroEvent.getHeaders().put(entry.getKey(), entry.getValue());
            }

            final List<AvroFlumeEvent> batch = batchSize > 1 ? events.addAndGet(avroEvent, batchSize) : null;
            if (batch == null && batchSize > 1) {
                return;
            }

            int i = 0;

            msg = "Error writing to " + getName();

            do {
                try {
                    final Status status = (batch == null) ? client.append(avroEvent) : client.appendBatch(batch);
                    if (!status.equals(Status.OK)) {
                        throw new AvroRemoteException("RPC communication failed to " + agents.get(current).getHost() +
                            ":" + agents.get(current).getPort());
                    }
                    return;
                } catch (final Exception ex) {
                    if (i == retries - 1) {
                        msg = "Error writing to " + getName() + " at " + agents.get(current).getHost() + ":" +
                            agents.get(current).getPort();
                        statusLogger.addWarn(msg, ex);
                        break;
                    }
                    sleep(delay);
                }
            } while (++i < retries);

            for (int index = 0; index < agents.size(); ++index) {
                if (index == current) {
                    continue;
                }
                final FlumeAgent agent = agents.get(index);
                i = 0;
                do {
                    try {
                        transceiver = null;
                        final AvroSourceProtocol c = connect(agent.getHost(), agent.getPort());
                        final Status status = (batch == null) ? c.append(avroEvent) : c.appendBatch(batch);
                        if (!status.equals(Status.OK)) {
                            if (i == retries - 1) {
                                final String warnMsg = "RPC communication failed to " + getName() + " at " +
                                    agent.getHost() + ":" + agent.getPort();
                                statusLogger.addWarn(warnMsg);
                            }
                            continue;
                        }
                        client = c;
                        current = i;
                        return;
                    } catch (final Exception ex) {
                        if (i == retries - 1) {
                            final String warnMsg = "Error writing to " + getName() + " at " + agent.getHost() + ":" +
                                agent.getPort();
                            statusLogger.addWarn(warnMsg, ex);
                            break;
                        }
                        sleep(delay);
                    }
                } while (++i < retries);
            }
        }

        throw new RuntimeException(msg);

    }

    private void sleep(final int delay) {
        try {
            Thread.sleep(delay);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * There is a very good chance that this will always return the first agent even if it isn't available.
     * @param agents The list of agents to choose from
     * @return The FlumeEventAvroServer.
     */
    private AvroSourceProtocol connect(final List<FlumeAgent> agents) {
        int i = 0;
        for (final FlumeAgent agent : agents) {
            final AvroSourceProtocol server = connect(agent.getHost(), agent.getPort());
            if (server != null) {
                current = i;
                return server;
            }
            ++i;
        }
        statusLogger.addError("Flume manager " + getName() + " was unable to connect to any agents");
        return null;
    }

    private AvroSourceProtocol connect(final String hostname, final int port) {
        try {
            if (transceiver == null) {
                transceiver = new NettyTransceiver(new InetSocketAddress(hostname, port));
            }
        } catch (final IOException ioe) {
            statusLogger.addError("Unable to create transceiver", ioe);
            return null;
        }
        try {
            return SpecificRequestor.getClient(AvroSourceProtocol.class, transceiver);
        } catch (final IOException ioe) {
            statusLogger.addError("Unable to create Avro client");
            return null;
        }
    }

    @Override
    protected void releaseSub() {
        if (transceiver != null) {
            try {
                transceiver.close();
            } catch (final IOException ioe) {
                statusLogger.addError("Attempt to clean up Avro transceiver failed", ioe);
            }
        }
        client = null;
    }

    /**
     * Thread-safe List management of a batch.
     */
    private static class EventList extends ArrayList<AvroFlumeEvent> {

        /**
         * Generated serial version ID.
         */
        private static final long serialVersionUID = -1599817377315957495L;

        public synchronized List<AvroFlumeEvent> addAndGet(final AvroFlumeEvent event, final int batchSize) {
            super.add(event);
            if (this.size() >= batchSize) {
                final List<AvroFlumeEvent> events = new ArrayList<AvroFlumeEvent>();
                events.addAll(this);
                clear();
                return events;
            } else {
                return null;
            }
        }
    }


}
