package javax.microedition.media;
public interface Player {
  int UNREALIZED = 100, REALIZED = 200, PREFETCHED = 300, STARTED = 400, CLOSED = 0;
  void realize() throws MediaException; void prefetch() throws MediaException; void start() throws MediaException;
  void stop() throws MediaException; void close(); void setLoopCount(int n);
  long setMediaTime(long t) throws MediaException;
  Object getControl(String n);
  int getState();
}
