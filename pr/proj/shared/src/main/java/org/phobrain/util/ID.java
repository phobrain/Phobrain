package org.phobrain.util;

/**
 **  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
 **
 **  SPDX-License-Identifier: AGPL-3.0-or-later
 **/

/**
 **  ID - extracting archive.picnum with variations like 1a, 11-1
 **
 */

import javax.naming.InvalidNameException;

public class ID implements Comparable {

    final public static String SPLIT_STR = "/";

    /** canonical for xx12,xx21 table ordering */

    public static void sortIds(String[] pairIds, String id1, String id2)
            throws InvalidNameException {

        ID bid1 = new ID(id1);
        ID bid2 = new ID(id2);

        if (ID.compare(bid1, bid2) < 0) {
            pairIds[0] = id1;
            pairIds[1] = id2;
        } else {
            pairIds[0] = id2;
            pairIds[1] = id1;
        }
    }

    public static String[] sortIds(String id1, String id2)
            throws InvalidNameException {

        String[] pairIds = new String[2];
        sortIds(pairIds, id1, id2);
        return pairIds;
    }

    @Override
    public int compareTo(Object o) {
        ID idx = (ID) o;
        return compare(this, idx);
    }

    public static int compare(ID id, ID idx) {

        if (id.arch < idx.arch) return -1;
        if (id.arch > idx.arch) return 1;
        if (id.seq < idx.seq) return -1;
        if (id.seq > idx.seq) return 1;
        if (id.id.length() < idx.id.length()) return -1;
        if (id.id.length() > idx.id.length()) return 1;

        // make 123a come before 123b

        return id.id.compareTo(idx.id);
/*
        // seq ==; seq2 follows python sort?
        int ct = 0;
        if (id.seq2 == -1) ct++;
        if (idx.seq2 == -1) ct++;
        if (ct == 1) {
            if (id.seq2 == -1) {
                return 1; // reversed
            }
            return -1;
        }
        if (id.seq2 < idx.seq2) return -1;
        if (id.seq2 > idx.seq2) return 1;
        // hacky
        if (id.id.endsWith(Integer.toString(id.seq))) {  // HACK
            //System.out.println("HHH " + id.id + " " + id.seq);
            return 1;
        }
        if (idx.id.endsWith(Integer.toString(idx.seq))) {  // HACK
            //System.out.println("vvv " + idx.id + " " + idx.seq);
            return -1;
        }
        return id.id.compareTo(idx.id);
*/
}

    public String fnameBody;

    public int arch = -1;
    public int seq = -1;
    public int seq2 = -1;
    public String tag = null;

    public boolean hardMatch = false;

    public String id;

    public ID(String id) throws InvalidNameException {

        String[] ii = id.split(SPLIT_STR);
        if (ii.length != 2) {
            throw new InvalidNameException("Bad split on '" + SPLIT_STR +
                                            "' " + id);
        }
        try {
            this.arch = Integer.parseInt(ii[0]);
        } catch (NumberFormatException nfe) {
            throw new InvalidNameException("Parsing arch on '" + SPLIT_STR +
                                            "' " + id +
                                            " " + nfe);
        }
        if (arch < 1) {
            throw new InvalidNameException("Invalid arch: " + arch);
        }
        this.fnameBody = "unk";
        // somewhat trusting
        this.id = MiscUtil.localStrip(id);
        parseSseq(ii[1]);
    }

    /**
     **  ID - use new FileRec() for most cases involving fname
     **/
    public ID(int arch, String fnameBody) throws InvalidNameException {

        if (arch < 1) {
            throw new InvalidNameException("Invalid arch: " + arch);
        }

        this.arch = arch;

        int ix = fnameBody.indexOf(".");
        if (ix != -1) {
            fnameBody = fnameBody.substring(0, ix);
        }
        this.fnameBody = fnameBody;

        // fnameBody -> id

        String s = MiscUtil.localStrip(fnameBody);

        // first digit
        int start = 0;
        for (start=0; start<s.length(); start++) {
            if (Character.isDigit(s.charAt(start))) {
                break;
            }
        }
        // first non-0 char
        for (; start<s.length(); start++) {
            if (s.charAt(start) != '0') {
                break;
            } 
        }
        if (start == s.length()) {
            throw new InvalidNameException(
                    "No pic number in file name: " + fnameBody);
        }

        int end = s.length();
        if (s.endsWith("_sm")) {
            end -= 3;
        } else if (s.endsWith("_srgb")) {
            end -= 5;
        }
        String sseq = s.substring(start, end);

        id = "" + arch + SPLIT_STR + sseq;

        parseSseq(sseq);
    }

    private void parseSseq(String sseq) throws InvalidNameException {

        // parse sseq into: seq [seq2]

        int start = -1;
        for (int i=0; i<sseq.length(); i++) {
            char c = sseq.charAt(i);
            if (c == '0') {
                continue;
            }
            if (Character.isDigit(sseq.charAt(i))) {
                start = i;
                break;
            }
        }
        if (start == -1) {
            throw new RuntimeException(
                    "ID.parseSseq: Expected a number in: [" + sseq + "] fnameBody: " + this.fnameBody);
        }
        int end = -1;
        for (int i=start+1; i<sseq.length(); i++) {
            if (!Character.isDigit(sseq.charAt(i))) {
                end = i;
                break;
            }
        }
        if (end == -1) {
            end = sseq.length();
        }
        String ss = sseq.substring(start, end);
        //System.out.println("id sseq " + id + " " + sseq);
        try {
            seq = Integer.parseInt(ss);
        } catch (NumberFormatException nfe) {
            throw new InvalidNameException(
                    "ID: Expected a sequence number: " +
                    sseq + ": " + nfe);
        }

        if (end < sseq.length()) {

            // try for seq2:
            //  1234-[1-5] for files
            //  1234=[1-5] for keywords

            char c = sseq.charAt(end);
            if (c == '=') {
                hardMatch = true;
                c = '-';
            }
            //System.out.println("C " + c);
            if (c == '-') {
                start = end + 1;
                end = -1;
                for (int i=start+1; i<sseq.length(); i++) {
                    if (!Character.isDigit(sseq.charAt(i))) {
                        end = i;
                        break;
                    }
                }
                if (end == -1) end = sseq.length();

                if (start == end) {
                    throw new InvalidNameException(
                                    "ID: id part2 hanging dash: " +
                                    sseq);
                }
                String sseq2 = sseq.substring(start, end);
                try {
                    seq2 = Integer.parseInt(sseq2);
                } catch (NumberFormatException nfe) {
                    throw new InvalidNameException("ID: parsing seq2: " +
                                 sseq + ": " + nfe);
                }
            }
            if (end < sseq.length()) {
                tag = sseq.substring(end);
            }
        }
    }

}
