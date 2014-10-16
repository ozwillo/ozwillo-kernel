package oasis.services.etag;

public interface EtagService {

  boolean hasEtag(Object o, long[] versions);

  String getEtag(Object o);

  long[] parseEtag(String etagStr);
}
