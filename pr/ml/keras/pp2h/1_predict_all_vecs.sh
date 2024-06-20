#!/bin/bash
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === 1_predict_all_vecs.sh

TARGET=VGG16

MLDIR="."
if [ $# -eq 1 ] ; then
    MLDIR=$1
fi

VEC_MODELS=/tmp/vecmodels.$$

find $MLDIR -name "*${TARGET}*.h5" >  $VEC_MODELS

N=`wc -l $VEC_MODELS | awk '{print $1}'`

if [ "$N" == "0" ] ; then
    echo No $TARGET in $MLDIR
    exit 1
fi

NVECS=`find $MLDIR -name "*.vecs" | grep $TARGET | wc -l`

N_GPU=`nvidia-smi -L | wc -l`

echo "== $0 GPUs: $N_GPU  Models: $N matching $TARGET  .vecs: $NVECS"

if [ $NVECS -gt 0 ] ; then
    echo === $0 Removing all $NVECS .vecs in $MLDIR in 5 sec
    sleep 5
    find $MLDIR -name "*.vecs" | grep $TARGET | while read i ; do rm $i; done
fi


time {

rm -f /tmp/spix/*

split -n l/$N_GPU $VEC_MODELS /tmp/spix

GPU=0
for i in /tmp/spix* ; do
    ppred.py -gpu $GPU -b -ifmt JvH -ofmt vecs $i &
    ((GPU=GPU+1))
done

FAIL=0
for job in `jobs -p`
do
    echo waiting on $job
    wait $job || let "FAIL+=1"
done
echo $FAIL

if [ "$FAIL" == "0" ]; then
    echo "YAY!"
else
    echo "FAIL! (Threads: $FAIL)"
fi

echo "== $0 DONE: $MLDIR Total vecs: `find $MLDIR -name "*.vecs" | wc -l`"
}
