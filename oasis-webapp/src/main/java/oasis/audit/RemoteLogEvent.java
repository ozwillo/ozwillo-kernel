package oasis.audit;

import java.util.Iterator;
import java.util.Map;

import org.joda.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class RemoteLogEvent extends LogEvent {
  private static final String TYPE = "remote";

  public RemoteLogEvent(Instant date) {
    super(TYPE, date);
  }

  public RemoteLogEvent setLog(ObjectNode log) {
    // XXX: handle  mandatory fields ?

    for (Iterator<Map.Entry<String, JsonNode>> iterator = log.fields(); iterator.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = iterator.next();
      this.addContextData(entry.getKey(), entry.getValue());
    }

    return this;
  }

}
