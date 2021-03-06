/**
 * Copyright (c) 2012 sndyuk <sanada@sndyuk.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.qos.logback.more.appenders;

import ch.qos.logback.classic.pattern.CallerDataConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.logstash.logback.argument.StructuredArgument;
import org.fluentd.logger.FluentLogger;
import org.slf4j.Marker;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class DataFluentAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final class FluentDaemonAppender extends DaemonAppender<ILoggingEvent> {
        private FluentLogger fluentLogger;
        private final String tag;
        private final String label;
        private final String remoteHost;
        private final int port;
        private final Map<String, String> additionalFields;
        private final DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        private final ObjectMapper objectMapper = new ObjectMapper();
        private final JsonFactory factory = new JsonFactory();


        FluentDaemonAppender(String tag,
                String label,
                String remoteHost,
                int port,
                int maxQueueSize,
                Map<String, String> additionalFields)
        {
            super(maxQueueSize);
            this.tag = tag;
            this.label = label;
            this.remoteHost = remoteHost;
            this.port = port;
            this.additionalFields = additionalFields;

        }

        @Override
        public void execute() {
            this.fluentLogger = FluentLogger.getLogger(tag, remoteHost, port);
            super.execute();
        }

        @Override
        protected void close() {
            try {
                super.close();
            } finally {
                if (fluentLogger != null) {
                    fluentLogger.close();
                }
            }
        }

        @Override
        protected void append(ILoggingEvent rawData) {
            final Map<String, Object> data = new HashMap<String, Object>();
            if (rawData.getMarker() != null) {
                Marker marker = rawData.getMarker();
                if(marker instanceof MapMarker) {
                    data.putAll(((MapMarker) marker).toMap());
                }else if(marker instanceof StructuredArgument){
                    StructuredArgument sa = (StructuredArgument)marker;
                    ByteArrayInOutStream os = new ByteArrayInOutStream();
                    try{
                        JsonGenerator generator = factory.createGenerator(os, JsonEncoding.UTF8);
                        generator.setCodec(objectMapper);
                        generator.writeStartObject();
                        sa.writeTo(generator);
                        generator.writeEndObject();
                        generator.close();

                        Map<String, Object> map  = objectMapper.readValue(os.getInputStream(), new TypeReference<HashMap<String, Object>>() {
                        });
                        data.putAll(map);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try{
                        os.close();
                    }catch (Exception e){

                    }

                }else{
                    data.put("marker", rawData.getMarker());
                }
            }
            data.put("message", rawData.getFormattedMessage());
            data.put("logger", rawData.getLoggerName());
            data.put("thread", rawData.getThreadName());
            data.put("level", rawData.getLevel());
            if (rawData.hasCallerData()) {
                data.put("caller", new CallerDataConverter().convert(rawData));
            }
            if (rawData.getThrowableProxy() != null) {
                data.put("throwable", ThrowableProxyUtil.asString(rawData.getThrowableProxy()));
            }
            if (additionalFields != null) {
                data.putAll(additionalFields);
            }
            for (Entry<String, String> entry : rawData.getMDCPropertyMap().entrySet()) {
                data.put(entry.getKey(), entry.getValue());
            }

            data.put("@timestamp", simpleDateFormat.format(new Date(rawData.getTimeStamp())));
            fluentLogger.log(label, data, rawData.getTimeStamp() / 1000);
        }
    }

    private DaemonAppender<ILoggingEvent> appender;

    // Max queue size of logs which is waiting to be sent (When it reach to the max size, the log will be disappeared).
    private int maxQueueSize;

    @Override
    protected void append(ILoggingEvent eventObject) {
        if (appender == null) {
            synchronized (this) {
                appender = new FluentDaemonAppender(tag, label, remoteHost, port, maxQueueSize, additionalFields);
            }
        }
        eventObject.prepareForDeferredProcessing();
        appender.log(eventObject);
    }

    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            appender.close();
        }
    }

    private String tag;
    private String label;
    private String remoteHost;
    private int port;
    private Map<String, String> additionalFields;

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void addAdditionalField(Field field) {
        if (additionalFields == null) {
            additionalFields = new HashMap<String, String>();
        }
        additionalFields.put(field.getKey(), field.getValue());
    }

    public static class Field {
        private String key;
        private String value;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}