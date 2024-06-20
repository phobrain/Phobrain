#!/bin/bash
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

echo forgot why this vs. 4_: $0
exit 1

# === 4_top_all_dirs.sh
#       Choose top 50+ per pic/side, with min 30 per archive
#       TODO: scale min by size of archive, e.g. 5..50

TOPARGS="-top 50 50 -o same"

JOBS=4

if [ $# != "1" ] ; then
    echo "Usage: $0 <dir> # ('.' is ok)"
    exit 1
fi

BASE=$1

LIST=/tmp/pairs_bin.$$
PREV=/tmp/prev_top.$$

if [[ "$BASE" == "." ]] ; then
    echo "=== $0 BASE=. so running all v, h"
    find $BASE -name "*.pairs_bin" > $LIST
    find $BASE -name "*.top" > $PREV

elif [[ "$BASE" == "v" || "$BASE" == "h" ]] ; then
    echo "=== $0 $1 so running all ${1}_20xx"
    \ls -d ${1}_2* | while read i ; do
        find $i -name "*.pairs_bin" >> $LIST
        find $i -name "*.top" >> $PREV
    done
else
    echo "=== $0 BASE $BASE"
    find $BASE -name "*.pairs_bin" > $LIST
    find $BASE -name "*.top " > $PREV
fi

N=`wc -l $LIST | awk '{print $1}'`
NV=`egrep '/v_|^v_|^vb_|/vb_' $LIST | wc -l`
NH=`egrep '/h_|^h_|^hb_|/hb_' $LIST | wc -l`

PN=`wc -l $PREV | awk '{print $1}'`
PNV=`egrep '/v_|^v_|^vb_|/vb_' $PREV | wc -l`
PNH=`egrep '/h_|^h_|^hb_|/hb_' $PREV | wc -l`

echo "=== $0 BASE $BASE JOBS $JOBS .pairs_bin: $N  V: $NV H: $NH  PREV v/h: $PNV $PNH"

START=`date`

set -e

if [ $PN -gt 0 ] ; then
    echo "== leaving $PN previous .tops"
fi

#echo "== doing [ $TOPARGS ] in $JOBS JOBS"

# note - loads data into predtool.py for each file, vs. old method
#   splits /tmp files to match size

time {

    cat $LIST | while read i ; do
        if [[ $i == *"/v_"* || $i == */"/vb_"* ]]; then
            echo predtool.py -v $TOPARGS $i
        elif [[ $i == "v_"* || $i == "vb_"* ]]; then
            echo predtool.py -v $TOPARGS $i
        elif [[ $i == *"/h_"* || $i == *"/hb_"* ]]; then
            echo predtool.py -h $TOPARGS $i
        elif [[ $i == "h_"* || $i == "hb_"* ]]; then
            echo predtool.py -h $TOPARGS $i
        else
            echo "== $0 unexpected: $i"
            echo "   -> No v_ or h_ in path"
            exit 1
        fi
    done | parallel -u --jobs $JOBS >> ~/pred.log
}
echo "== $0 $1 DONE"
echo START $START
echo "END   `date`"
