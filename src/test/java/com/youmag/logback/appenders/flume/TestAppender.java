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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
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

import java.io.File;
import java.io.FileReader;
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

    private LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    @Before
    public void initiate() throws Exception{
        int port = 4141;
        source = new AvroSource();
        ch = new MemoryChannel();
        Configurables.configure(ch, new Context());

        Context context = new Context();
        context.put("port", String.valueOf(port));
        context.put("bind", "127.0.0.1");
        Configurables.configure(source, context);

        configureSource();

        File TESTFILE = new File(
                TestAppender.class.getClassLoader()
                        .getResource("flume-logbacktest.xml").getFile());


        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);

        configurator.doConfigure(TESTFILE);

        StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);

    }


    private void configureSource() {
        List<Channel> channels = new ArrayList<Channel>();
        channels.add(ch);

        ChannelSelector rcs = new ReplicatingChannelSelector();
        rcs.setChannels(channels);

        source.setChannelProcessor(new ChannelProcessor(rcs));

        source.setName("test");

        source.start();
    }
    @Test
    public void testLogbackAppender() throws IOException {

        //configureSource();
        Logger logger = loggerContext.getLogger(TestAppender.class);
        for(int count = 0; count <= 1000; count++){
      /*
       * Log4j internally defines levels as multiples of 10000. So if we
       * create levels directly using count, the level will be set as the
       * default.
       */
            //    int level = ((count % 5)+1)*10000;
            String msg = "This is log message number" + String.valueOf(count);

            // doLog(logger, Level.toLevel(level), msg);

            logger.info(msg);

            Transaction transaction = ch.getTransaction();
            transaction.begin();
            Event event = ch.take();
            Assert.assertNotNull(event);
            Assert.assertEquals(new String(event.getBody(), "UTF8"), msg);

            Map<String, String> hdrs = event.getHeaders();

           // logger.info("Received from channel: "+new String(event.getBody()));

            transaction.commit();
            transaction.close();
        }

    }
  /*
  @Test
  public testLogbackAppenderFailureUnsafeMode() throws Throwable {
    configureSource();
    props.setProperty("log4j.appender.out2.UnsafeMode", String.valueOf(true));
    Logger logger = loggerContext.getLogger(TestLogbackAppender.class);
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
    private void doLog(Logger logger, Level level, String msg){
        logger.log(null, TestAppender.class.getCanonicalName(), level.levelInt, msg, null, null);

    }

    private void sendAndAssertFail(Logger logger) throws Throwable {
      /*
       * Log4j internally defines levels as multiples of 10000. So if we
       * create levels directly using count, the level will be set as the
       * default.
       */
        int level = 20000;
        try {
            //    doLog(logger, Level.toLevel(level), "Test Msg");
            logger.info("Test Msg");
        } catch (FlumeException ex) {
            ex.printStackTrace();
            throw ex.getCause();
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
        props.put("log4j.appender.out2.Timeout", "1000");
        props.put("log4j.appender.out2.layout", "org.apache.log4j.PatternLayout");
        props.put("log4j.appender.out2.layout.ConversionPattern",
                "%-5p [%t]: %m%n");
        Logger logger = loggerContext.getLogger(TestAppender.class);
        Thread.currentThread().setName("Log4jAppenderTest");
        int level = 10000;
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
    }


    @After
    public void cleanUp(){
        source.stop();
        ch.stop();
    }
        */

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
