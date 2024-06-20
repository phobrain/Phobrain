#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

DEST=`../../bin/get_phobrain_bulk_db_dump_store.sh`

set -e
echo "== $0 - files to move:"
ls -l pr.pairs_[vh]*

echo "Moving pr.pairs_[vh] to $DEST with old ones moved to $DEST/bak/ in 5"

sleep 5

time {
    for i in pr.pair* ; do
        echo mv $i $DEST
        if [ -f $DEST/$i ] ; then
            mv $DEST/$i $DEST/bak
        fi
        mv $i $DEST/
    done
}
