package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  HashCount - count instances of strings
 **
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Comparator;
import java.util.Collections;

import java.lang.StringBuilder;

public class HashCount {

    private final Map<String, Integer> map = new HashMap<>();

    public int size() {
        return map.size();
    }

    public int totalCases() {

        int ret = 0;

        for (Map.Entry entry : map.entrySet()) {
            ret += (Integer) entry.getValue();
        }

        return ret;
    }

    public int add(String s) {

        if (s == null) {
            return -1;
        }

        Integer i = map.get(s);
        if (i == null) {
            map.put(s, 1);
            return 1;
        }
        map.put(s, i+1);
        return i+1;
    }

    public int add(String s, int n) {

        if (s == null) {
            return -1;
        }

        Integer i = map.get(s);
        if (i == null) {
            map.put(s, n);
            return n;
        }

        map.put(s, i+n);
        return i+n;
    }

    public int add(HashCount hc) {
        for (Map.Entry entry : hc.map.entrySet()) {
            String key = (String) entry.getKey();
            int value = (Integer) entry.getValue();

            Integer i = map.get(key);
            if (i == null) {
                map.put(key, value);
            } else {
                map.put(key, i+value);
            }
        }
        return map.size();
    }

    public void remove(String s) {
        map.remove(s);
    }

    public int getCount0(String s) {
        int x = getCount(s);
        return x == -1 ? 0: x;
    }

    public int getCount(String s) {
        if (s == null) {
            return -1;
        }
        Integer i = map.get(s);
        if (i == null) {
            return -1;
        }
        return i;
    }

    public Set<String> keySet() {
        return map.keySet();
    }

    public Map.Entry[] sortedMap() {
        Map<String, Integer> m = MapUtil.sortByValue(map);
        Set<Map.Entry<String, Integer>> es = m.entrySet();
        Map.Entry sorted[] = new Map.Entry[es.size()];
        m.entrySet().toArray(sorted);
        return sorted;
    }

    public int countSets() {
        if (map.size() == 0) {
            return 0;
        }
        Map<String, Integer> m = MapUtil.sortByValue(map);
        Set<Integer> set = new HashSet<>();
        for (Map.Entry pair : map.entrySet()) {
            Integer val = (Integer) pair.getValue();
            set.add(val);
        }
        return set.size();
    }

    public List<Set<String>> getSetsInOrder() {
        if (map.size() == 0) {
            return null;
        }
        // in descending order

        Map.Entry sorted[] = sortedMap();

        int max = (int) sorted[sorted.length-1].getValue();

        Set<String> sets[] = new Set[max+1];

        for (Map.Entry pair : sorted) {
            //System.out.println(" " + pair.getValue() + " " + pair.getKey());
            int val = (int) pair.getValue();
            if (sets[val] == null) {
                sets[val] = new HashSet<>();
            }
            sets[val].add((String) pair.getKey());
        }
        StringBuilder sb = new StringBuilder();
        List<Set<String>> ret = new ArrayList<>();
        // in descending order
        for (int i=sets.length-1; i>-1; i--) {
            if (sets[i] != null) {
                ret.add(sets[i]);
                sb.append(i).append(": ").append(sets[i].size()).append(" ");
            }
        }
        //System.out.println("Sets: " + sb);
        return ret;
/*

        Set<String> ret = new HashSet<>();
        for (int i=sorted.length-1; i>-1; i--) {
            if ((int)sorted[i].getValue() != max) {
                break;
            }
            ret.add((String) sorted[i].getKey());
        }
        return ret;

        for (Map.Entry pair : sorted) {
            System.out.println(" " + pair.getValue() + " " + pair.getKey());
        }
        return (String) sorted[sorted.length-1].getKey();
*/
    }

    public String toString() {
        return toString(true, "");
    }
    public String toString(boolean value) {
        return toString(value, "");
    }
    public String toString(String lineStart) {
        return toString(true, lineStart);
    }

    public String toString(boolean value, String lineStart) {

        if (map.size() == 0) {
            return "no entries";
        }

        if (lineStart == null) {
            lineStart = "\n";
        } else {
            lineStart = "\n" + lineStart;
        }

        StringBuilder sb = new StringBuilder();

        if (value) {

            sb.append(lineStart)
              .append("count   key");

            Map<String, Integer> m = MapUtil.sortByValue(map);

            for (Map.Entry pair : m.entrySet()) {
                sb.append(lineStart)
                  .append(pair.getValue())
                  .append("\t")
                  .append(pair.getKey());
                  //.append(lineStart);
            }

        } else {   // key

            sb.append(lineStart)
              .append("key   count");

            Set<String> keys = map.keySet();

            boolean intKeys = true;
            for (String key : keys) {
                for (int i=0; intKeys && i<key.length(); i++) {
                    if (!Character.isDigit(key.charAt(i))) {
                        intKeys = false;
                        break;
                    }
                }
            }

            if (intKeys) {
                List<String> ikeys = new ArrayList<>(keys);
                Collections.sort(ikeys, new Comparator<String>() {
                    public int compare(String s1, String s2) {
                        int i1 = Integer.parseInt(s1.replaceAll("\\D", ""));
                        int i2 = Integer.parseInt(s2.replaceAll("\\D", ""));
                        return Integer.compare(i1, i2);
                    }
                });
                for (String key : ikeys) {
                    sb.append(lineStart)
                      .append(key)
                      .append(": ")
                      .append(map.get(key));
                      //.append(lineStart);
                }
            } else {

                // general keys-as-Strings

                Map<String, Integer> m = new TreeMap<String, Integer>(map);

                for (Map.Entry pair : m.entrySet()) {
                    sb.append(lineStart)
                      .append(pair.getKey())
                      .append(": ")
                      .append(pair.getValue());
                      //.append(lineStart);
                }
            }
        }
        return sb.toString();
    }

    public void clear() {
        map.clear();
    }
}
