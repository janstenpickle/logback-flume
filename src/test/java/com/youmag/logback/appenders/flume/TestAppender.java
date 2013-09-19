/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.youmag.logback.appenders.flume;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import junit.framework.Assert;
import org.apache.flume.*;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.channel.MemoryChannel;
import org.apache.flume.channel.ReplicatingChannelSelector;
import org.apache.flume.conf.Configurables;
import org.apache.flume.source.AvroSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class TestAppender {
    private AvroSource source;
    private Channel ch;
    private Properties props;
    private File LOGBACK_CONFIG;
    private File LOGBACK_CONFIG_CONSOLE;
    private File LOGBACK_CONFIG_ASYNC;

    JoranConfigurator configurator;

    private LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    @Before
    public void initiate() throws Exception {
        int port = 4141;
        source = new AvroSource();
        ch = new MemoryChannel();
        Configurables.configure(ch, new Context());

        Context context = new Context();
        context.put("port", String.valueOf(port));
        context.put("bind", "127.0.0.1");
        Configurables.configure(source, context);

        configureSource();

        LOGBACK_CONFIG = new File(
                TestAppender.class.getClassLoader()
                        .getResource("flume-logbacktest.xml").getFile());

        configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);

        configurator.doConfigure(LOGBACK_CONFIG);
        configurator.registerSafeConfiguration();
        StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);


    }


    private void configureSource() {

        //logger.info("Configuring ");
        List<Channel> channels = new ArrayList<Channel>();
        channels.add(ch);

        ChannelSelector rcs = new ReplicatingChannelSelector();
        rcs.setChannels(channels);

        source.setChannelProcessor(new ChannelProcessor(rcs));

        source.setName("test");

        source.start();
    }

    @Test
    public void testLogbackAppender() throws IOException, JoranException {
        MDC.put("test", "hello");
        Logger logger = loggerContext.getLogger(TestAppender.class);

        //Clear all events out of channel before starting the real test
        //This is because the logging events from creating the channel inside this test
        Transaction trans = ch.getTransaction();
        trans.begin();
        while (true) {
            Event e = ch.take();
            if (e == null) {
                trans.commit();
                trans.close();
                break;
            }
        }


        for (int count = 0; count <= 1000; count++) {
      /*
       * Log4j internally defines levels as multiples of 10000. So if we
       * create levels directly using count, the level will be set as the
       * default.
       */
            //    int level = ((count % 5)+1)*10000;
            String msg = "This is log message number" + String.valueOf(count);

            // doLog(logger, Level.toLevel(level), msg);

            logger.info("This is log message number{}",String.valueOf(count));

            Transaction transaction = ch.getTransaction();
            transaction.begin();
            Event event = ch.take();
            Assert.assertNotNull(event);

            Assert.assertEquals(new String(event.getBody(), "UTF8"), msg);

            Map<String, String> hdrs = event.getHeaders();

            System.out.println(hdrs);

            // logger.info("Received from channel: "+new String(event.getBody()));

            transaction.commit();
            transaction.close();
        }


    }
    /*
  @Test
  public void testLogbackAppenderFailureUnsafeMode() throws Throwable {

      Logger logger = loggerContext.getLogger(TestAppender.class);

    source.stop();
    sendAndAssertFail(logger);

  }

    @Test(expected = EventDeliveryException.class)
    public void testLogbackAppenderFailureNotUnsafeMode() throws Throwable {
        configureSource();
        Logger logger = loggerContext.getLogger(TestAppender.class);
        source.stop();
        sendAndAssertFail(logger);

    }
             */


    private void sendAndAssertFail(Logger logger) throws Throwable {
      /*
       * Log4j internally defines levels as multiples of 10000. So if we
       * create levels directly using count, the level will be set as the
       * default.
       */
        try {
            //    doLog(logger, Level.toLevel(level), "Test Msg");
            logger.info("Test Msg");
        } catch (FlumeException ex) {
            ex.printStackTrace();
            //throw ex.getCause();
        }
        Transaction transaction = ch.getTransaction();
        transaction.begin();
        Event event = ch.take();
        Assert.assertNull(event);
        transaction.commit();
        transaction.close();

    }


     /*

    @Test(expected = EventDeliveryException.class)
    public void testSlowness() throws Throwable {
        ch = new SlowMemoryChannel(2000);
        Configurables.configure(ch, new Context());
        configureSource();

        configurator.doConfigure(LOGBACK_CONFIG);
        Logger logger = loggerContext.getLogger(TestAppender.class);
        Thread.currentThread().setName("Log4jAppenderTest");
        String msg = "This is log message number" + String.valueOf(1);
        try {
            logger.info(msg);
        } catch (FlumeException ex) {
            throw ex.getCause();
        }
    }

    @Test // Should not throw
    public void testSlownessUnsafeMode() throws Throwable {
        props.setProperty("log4j.appender.out2.UnsafeMode", String.valueOf(true));
        testSlowness();
    }  */


    @After
    public void cleanUp() throws JoranException {
        //Deconfigure flume appender
        Logger logger = loggerContext.getLogger(TestAppender.class);

        //logger.info("Deconfiguring");
        source.stop();
        ch.stop();
    }


    static class SlowMemoryChannel extends MemoryChannel {
        private final int slowTime;

        public SlowMemoryChannel(int slowTime) {
            this.slowTime = slowTime;
        }

        public void put(Event e) {
            try {
                TimeUnit.MILLISECONDS.sleep(slowTime);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            super.put(e);
        }
    }

}
