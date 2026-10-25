package javax.microedition.rms;
import java.util.Vector;
// Desktop test stand-in: in memory only.
public class RecordStore {
  static java.util.Hashtable stores = new java.util.Hashtable();
  Vector recs = new Vector();
  public static void deleteRecordStore(String n) { stores.remove(n); }
  public static RecordStore openRecordStore(String n, boolean c) {
    RecordStore r = (RecordStore) stores.get(n);
    if (r == null && !c) throw new RuntimeException("RecordStoreNotFoundException " + n);
    if (r == null) { r = new RecordStore(); stores.put(n, r); }
    return r;
  }
  public int getNumRecords() { return recs.size(); }
  public byte[] getRecord(int id) { return (byte[]) recs.elementAt(id - 1); }
  public void setRecord(int id, byte[] b, int o, int l) { byte[] c = new byte[l]; System.arraycopy(b, o, c, 0, l); recs.setElementAt(c, id - 1); }
  public int addRecord(byte[] b, int o, int l) { byte[] c = new byte[l]; System.arraycopy(b, o, c, 0, l); recs.addElement(c); return recs.size(); }
  public void closeRecordStore() {}
}
