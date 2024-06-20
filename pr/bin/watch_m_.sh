#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === watch_m_.sh - print the names of m_[vh]_... model files 
#           as they are trained.

INTERVAL="5"

if [ $# -eq 1 ] ; then
    #echo args $# == $1
    INTERVAL="$1"
fi

echo "Interval = $INTERVAL"


TMP=/tmp/XW.$$
CANON=/tmp/M_

/bin/rm -f "$CANON"

while [ 1 ] ; do

    \ls -tr m_* 2>/dev/null > $TMP
    N=`wc -l $TMP|awk '{print $1}'`
    if [ $N == "0" ] ; then
        sleep $INTERVAL
        continue
    fi

    if [ -e $CANON ] ; then
        #echo DIFF
        diff $CANON $TMP | awk '$1 == ">" {print $2}'
    else
        echo START
        cat $TMP
        echo
    fi
    cp $TMP $CANON

    sleep "$INTERVAL"
done
