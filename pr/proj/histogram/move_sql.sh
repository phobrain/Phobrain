#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

set -e

DEST=`../../bin/phobrain_property.sh bulk.db.dump.dir`

function list()
{
    ls -l $1 | awk '$4~/./{for (i=1;i<4;i++){$i=""}; gsub(/^ +/,""); print}'
}

echo
echo "== $0 - pr.pairtop_ files:"
ls -l pr.pairtop_*

echo
echo "--- Compare local/dest/bak:"
echo

ls -1 pr.pairtop_* | while read i ; do
    list ./$i 
    list $DEST/$i 
    list $DEST/bak/$i
    echo
done

echo "--- Moving all pr.pairtop_ to $DEST"
echo "     with old ones moved to $DEST/bak/"
echo
echo "  -> in 5 seconds"

sleep 5

time {
    for i in pr.pairtop_* ; do
        echo " -- $i "
        if [ -f $DEST/$i ] ; then
            mv $DEST/$i $DEST/bak
        fi
        mv $i $DEST/
    done
}
