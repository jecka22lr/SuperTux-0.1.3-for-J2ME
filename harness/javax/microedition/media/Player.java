package javax.microedition.media;
public interface Player {
  void realize() throws MediaException; void prefetch() throws MediaException; void start() throws MediaException;
  void stop() throws MediaException; void close(); void setLoopCount(int n);
  long setMediaTime(long t) throws MediaException;
}
