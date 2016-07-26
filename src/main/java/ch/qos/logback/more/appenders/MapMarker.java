package ch.qos.logback.more.appenders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Marker;

import java.util.*;

public class MapMarker implements Marker {
  private final String name;
  private List<Marker> referenceList;
  private Map<String, Object> obj = null;

  private static ObjectMapper objectMapper  = new ObjectMapper();

  private static String MARKER_PREFIX = "JDM-_-";

  private MapMarker(String name) {
    this.name = MARKER_PREFIX + name;
  }

  public MapMarker(){
    name = MARKER_PREFIX + "DEF";
  }
  public static MapMarker map(Map<String, Object> obj){
    MapMarker m = new MapMarker("MAP");
    m.obj = obj;
    return m;
  }

  public static MapMarker map(Object classObj){
    MapMarker m = new MapMarker("OBJ");
    m.obj = objectMapper.convertValue(classObj, new TypeReference<HashMap<String,Object>>() {});
    return m;
  }

  public Map<String, Object> toMap(){
    return obj;
  }

  @Override
  public String getName() {
    return name;
  }

  public synchronized void add(Marker reference) {
    if (reference == null) {
      throw new IllegalArgumentException(
              "A null value cannot be added to a Marker as reference.");
    }

    // no point in adding the reference multiple times
    if (this.contains(reference)) {
      return;

    } else if (reference.contains(this)) { // avoid recursion
      // a potential reference should not its future "parent" as a reference
      return;
    } else {
      // let's add the reference
      if (referenceList == null) {
        referenceList = new Vector<Marker>();
      }
      referenceList.add(reference);
    }

  }

  public synchronized boolean hasReferences() {
    return ((referenceList != null) && (referenceList.size() > 0));
  }

  public boolean hasChildren() {
    return hasReferences();
  }

  public synchronized Iterator<Marker> iterator() {
    if (referenceList != null) {
      return referenceList.iterator();
    } else {
      return Collections.EMPTY_LIST.iterator();
    }
  }

  public synchronized boolean remove(Marker referenceToRemove) {
    if (referenceList == null) {
      return false;
    }

    int size = referenceList.size();
    for (int i = 0; i < size; i++) {
      Marker m = (Marker) referenceList.get(i);
      if (referenceToRemove.equals(m)) {
        referenceList.remove(i);
        return true;
      }
    }
    return false;
  }

  public boolean contains(Marker other) {
    if (other == null) {
      throw new IllegalArgumentException("Other cannot be null");
    }

    if (this.equals(other)) {
      return true;
    }

    if (hasReferences()) {
      for (int i = 0; i < referenceList.size(); i++) {
        Marker ref = (Marker) referenceList.get(i);
        if (ref.contains(other)) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean contains(String name) {
    if (name == null) {
      throw new IllegalArgumentException("Other cannot be null");
    }

    if (this.name.equals(name)) {
      return true;
    }

    if (hasReferences()) {
      for (int i = 0; i < referenceList.size(); i++) {
        Marker ref = (Marker) referenceList.get(i);
        if (ref.contains(name)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String OPEN = "[ ";
  private static String CLOSE = " ]";
  private static String SEP = ", ";

  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof Marker))
      return false;

    final Marker other = (Marker) obj;
    return name.equals(other.getName());
  }

  public int hashCode() {
    return name.hashCode();
  }

  public String toString() {
    if (!this.hasReferences()) {
      return this.getName();
    }
    Iterator<Marker> it = this.iterator();
    Marker reference;
    StringBuffer sb = new StringBuffer(this.getName());
    sb.append(' ').append(OPEN);
    while (it.hasNext()) {
      reference = (Marker) it.next();
      sb.append(reference.getName());
      if (it.hasNext()) {
        sb.append(SEP);
      }
    }
    sb.append(CLOSE);

    return sb.toString();
  }

}
