package oasis.audit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class HttpLogEvent extends LogEvent {
  private static final Logger logger = LoggerFactory.getLogger(HttpLogEvent.class);
  private static final String TYPE = "http_request";
  private static final String URL = "url";
  private static final String METHOD = "method";
  private static final String HEADERS = "headers";
  private static final String STATUS = "status";
  private static final String START_TIME = "start_time";
  private static final String END_TIME = "end_time";
  private static final String ELAPSED_TIME = "elapsed_time";

  public HttpLogEvent() {
    super(TYPE);
  }

  public HttpLogEvent setUrl(String url) {
    this.addContextData(URL, url);
    return this;
  }

  public HttpLogEvent setMethod(String method) {
    this.addContextData(METHOD, method);
    return this;
  }

  public HttpLogEvent setHeaders(ImmutableMap<String, Object> headers) {
    this.addContextData(HEADERS, headers);
    return this;
  }

  public HttpLogEvent setStatus(int status) {
    this.addContextData(STATUS, status);
    return this;
  }

  public HttpLogEvent setStartTime(@Nullable Long startTime) {
    if (startTime != null) {
      this.addContextData(START_TIME, startTime);
    }
    return this;
  }

  public HttpLogEvent setEndTime(@Nullable Long endTime) {
    if (endTime != null) {
      this.addContextData(END_TIME, endTime);
    }
    return this;
  }

  public HttpLogEvent setElapsedTime(@Nullable Long duration) {
    if (duration != null) {
      this.addContextData(ELAPSED_TIME, duration);
    }
    return this;
  }

  @Override
  protected boolean checkBeforeBuild() {
    for (String key : ImmutableList.of(URL, METHOD, HEADERS, STATUS)) {
      if (!this.getContextMap().containsKey(key)) {
        logger.error("Key {} missing in the log event of type {}", key, this.getClass().getName());
        return false;
      }
    }
    return true;
  }
}
