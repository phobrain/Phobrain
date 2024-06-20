#!/bin/bash
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === 2_predict_all_jh.sh
#       - Second post-training step in adding more photos.

# Runs each predict_jh.sh below current or arg dir 
#   to generate batch_preds file, then runs them 
#   using gpu_q.py.

set -e

BASE="."

if [ $# -eq 1 ] ; then
    BASE="$1"
fi

echo == $0  Base is $BASE

START=`date`

CMD_LIST="batch_preds.$$"

# get list of cmds for batching on GPUs

find $BASE -type d -name "jh*" | while read dir ; do
    ORIENT=-v
    if [[ "$dir" == *"/h_"* ]]; then
        ORIENT=-h
    elif [[ "$dir" == *"/hb_"* ]]; then
        ORIENT=-h
    fi
    echo ppred.py $ORIENT -ifmt V ${dir}_vecs -ofmt bin $dir 
done | sort -r > $CMD_LIST


# process shorter orientation first

#if [ $NV -lt $NH ] ; then
#    echo "== $0 v's first"
#    sort -r $PREDS > x
#else
#    echo "== $0 h's first"
#    sort $PREDS > x
#fi
#mv x $PREDS

time {
gpu_q.py $CMD_LIST
echo == $0 DONE
}
/bin/rm -f $CMD_LIST
