package oasis.services.etag;

public interface EtagService {

  String getEtag(Object o);

  long[] parseEtag(String etagStr);
}
