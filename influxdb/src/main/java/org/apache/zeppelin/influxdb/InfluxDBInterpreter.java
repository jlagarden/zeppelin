/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.influxdb;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.Iterator;

import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.apache.zeppelin.interpreter.InterpreterUtils;
import org.apache.zeppelin.interpreter.thrift.InterpreterCompletion;
import org.apache.zeppelin.scheduler.Scheduler;
import org.apache.zeppelin.scheduler.SchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Pong;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

/**
 * InfluxDB interpreter for Zeppelin.
 */
public class InfluxDBInterpreter extends Interpreter {
  private static final Logger LOGGER = LoggerFactory.getLogger(InfluxDBInterpreter.class);

  private static final char WHITESPACE = ' ';
  private static final char NEWLINE = '\n';
  private static final char TAB = '\t';
  private static final String EMPTY_COLUMN_VALUE = "";
  private static final String TABLE_MAGIC_TAG = "%table ";

  static {
    Interpreter.register("influxdb", InfluxDBInterpreter.class.getName());
  }

  public InfluxDBInterpreter(Properties property) {
    super(property);
  }

  @Override
  public void open() {
    for (Map.Entry<Object, Object> e : property.entrySet()) {
      String key = e.getKey().toString();
      String value = (e.getValue() == null) ? "" : e.getValue().toString();
      LOGGER.debug("Property: key: {}, value: {}", key, value);
    }
  }

  @Override
  public InterpreterResult interpret(String cmd, InterpreterContext interpreterContext) {
    LOGGER.info("Run influxDB command '{}'", cmd);

    StringBuilder msg = new StringBuilder();
    try {
      InfluxDB idb = InfluxDBFactory.connect(property.getProperty("url"),
          property.getProperty("user"), property.getProperty("password"));

      QueryResult query = idb.query(new Query(cmd, "_internal"));

      if (query.hasError()) {
        LOGGER.error("InfluxDB Query Error ", query.getError());
        msg.append(query.getError());
      } else {
        msg.append(TABLE_MAGIC_TAG);
        List<QueryResult.Result> results = query.getResults();
        LOGGER.info("InfluxDB Query result '{}'", results.toString());
        QueryResult.Series data = results.get(0).getSeries().get(0);
        Iterator<String> columns = data.getColumns().iterator();
        Iterator<List<Object>> rows = data.getValues().iterator();
        while (columns.hasNext()) {
          msg.append(sanitize(columns.next()));
          if (columns.hasNext()) {
            msg.append(TAB);
          } else {
            msg.append(NEWLINE);
          }
        }
        while (rows.hasNext()) {
          Iterator<Object> row = rows.next().iterator();
          while (row.hasNext()) {
            Object elem = row.next();
            elem = (elem != null) ? elem : "";
            msg.append(sanitize(elem.toString()));
            if (row.hasNext()) {
              msg.append(TAB);
            } else {
              msg.append(NEWLINE);
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Error ", e);
      msg = new StringBuilder();
      msg.append(e);
    }
    return new InterpreterResult(Code.SUCCESS, msg.toString());
  }

  @Override
  public FormType getFormType() {
    return FormType.SIMPLE;
  }

  @Override
  public int getProgress(InterpreterContext context) {
    return 0;
  }

  @Override
  public Scheduler getScheduler() {
    return SchedulerFactory.singleton().createOrGetParallelScheduler(
        InfluxDBInterpreter.class.getName() + this.hashCode(), 5);
  }

  @Override
  public List<InterpreterCompletion> completion(String buf, int cursor) {
    return null;
  }

  @Override
  public void cancel(InterpreterContext context) {}

  @Override
  public void close() {}

  /**
 * For %table response replace Tab and Newline characters from the content.
 */
  private String sanitize(String str) {
    if (str == null) {
      return EMPTY_COLUMN_VALUE;
    }
    return str.replace(TAB, WHITESPACE).replace(NEWLINE, WHITESPACE);
  }
}
