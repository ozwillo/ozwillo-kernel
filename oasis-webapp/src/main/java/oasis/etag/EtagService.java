package oasis.etag;

public interface EtagService {

  String getEtag(Object o);

  long[] parseEtag(String etagStr);
}
