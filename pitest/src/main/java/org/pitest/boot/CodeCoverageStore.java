/*
 * Based on http://code.google.com/p/javacoveragent/ by
 * "alex.mq0" and "dmitry.kandalov"
 * 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.pitest.boot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store for line visit information.
 */
public final class CodeCoverageStore {

  private final static int                     CLASS_HIT_INDEX                           = 0;

  public static final String                   CODE_COVERAGE_CALCULATOR_CLASS_NAME       = CodeCoverageStore.class
                                                                                             .getName()
                                                                                             .replace(
                                                                                                 '.',
                                                                                                 '/');
  public static final String                   CODE_COVERAGE_CALCULATOR_CODE_METHOD_NAME = "visitLine";
  public static final String                   CODE_COVERAGE_CALCULATOR_CODE_METHOD_DESC = "(J)V";

  private static InvokeReceiver                invokeQueue;
  private static int                           classId                                   = 0;

  // array of probe hits, first slot indicates any hits to the class.
  // testing suggests boolean array with synchronization to ensure happens
  // before relationship significantly outperforms
  // both AtomicInteger array with bit per flag and integer per flag.
  // optimisation with other methods of ensuring a happens before not yet
  // investigated
  private final static Map<Integer, boolean[]> classHits                                 = new ConcurrentHashMap<Integer, boolean[]>();

  private final static Map<Integer, int[]>     classProbeToLineMapping                   = new ConcurrentHashMap<Integer, int[]>();

  public static void init(final InvokeReceiver invokeQueue) {
    CodeCoverageStore.invokeQueue = invokeQueue;
  }

  private CodeCoverageStore() {
  }

  public static void visitLine(final long lineId) { // NO_UCD
    final int line = decodeLineId(lineId);
    final int clazz = decodeClassId(lineId);
    final boolean[] bs = classHits.get(clazz);
    bs[CLASS_HIT_INDEX] = true;
    bs[line + 1] = true;
  }

  public synchronized static void reset() {
    for (final Entry<Integer, boolean[]> each : classHits.entrySet()) {
      classHits.put(each.getKey(), new boolean[each.getValue().length]);
    }
  }

  public synchronized static Collection<Long> getHits() {
    final Collection<Long> lineHits = new ArrayList<Long>();
    for (final Entry<Integer, boolean[]> each : classHits.entrySet()) {
      final boolean[] bs = each.getValue();
      // first entry tracks if class has been visited at all
      if (!bs[CLASS_HIT_INDEX]) {
        continue;
      }
      final int classId = each.getKey();
      final int[] mapping = classProbeToLineMapping.get(classId);
      for (int probeId = 1; probeId != bs.length; probeId++) {
        if (bs[probeId]) {
          lineHits.add(encode(classId, mapping[probeId - 1]));
        }
      }
    }
    return lineHits;
  }

  public static int registerClass(final String className) {
    final int id = nextId();
    invokeQueue.registerClass(id, className);
    return id;
  }

  private synchronized static int nextId() {
    return classId++;
  }

  public static int decodeClassId(final long value) {
    return (int) (value >> 32);
  }

  public static int decodeLineId(final long value) {
    return (int) (value & 0xFFFFFFFF);
  }

  public static long encode(final int classId, final int line) {
    return ((long) classId << 32) | line;
  }

  public static void registerClassProbes(final int classId,
      final int[] probeToLines) {
    classHits.put(classId, new boolean[probeToLines.length + 1]);
    classProbeToLineMapping.put(classId, probeToLines);
  }

  public static void resetAllStaticState() {
    classHits.clear();
    classProbeToLineMapping.clear();
  }

}
