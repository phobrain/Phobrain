#!/bin/bash
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === 0_predict_list_vecs.sh

VEC_OUTPUTS=

if [ $# == 1 ] ; then

    VEC_OUTPUTS=$1

else

    VEC_OUTPUTS=/tmp/vecs.$$
    awk '$1 == "pairvecs" {print $2}' ~/phobrain_local/picture_vectors > $VEC_OUTPUTS
fi

N=`cat $VEC_OUTPUTS | wc  -l`

if [ $N == "0" ] ; then
    echo No lines in $VEC_OUTPUTS
    exit 1
fi

N_GPU=`nvidia-smi -L | wc -l`

echo "== $0"
echo "   GPUS ($N_GPU) VECS ($N):"
cat $VEC_OUTPUTS | sed 's/^/    /'
echo

echo "=== $0"
echo "    Removing any of the .vecs in 5 sec"
sleep 5

cat $VEC_OUTPUTS | while read i ; do
    rm -f $i
done


time {

sed -e 's:.vecs:.h5:g' $VEC_OUTPUTS > /tmp/h5.$$

rm -f /tmp/splix*

split -n r/$N_GPU /tmp/h5.$$ /tmp/splix

#wc -l /tmp/h5.$$ /tmp/splix*

GPU=0
for i in /tmp/splix* ; do
    ppred.py -gpu $GPU -b -ifmt JvH -ofmt vecs $i &
    ((GPU=GPU+1))
done

echo "== waiting on $GPU ppred.py procs"

FAIL=0
for i in /tmp/splix* ; do
    wait -n
    ret="$?"
    let "FAIL+=$ret"
    #echo ====WWWW $ret
done

echo "== $0 DONE: $MLDIR Models: $N"

if [ "$FAIL" == "0" ]; then
    echo OK
    exit 0
else
    echo "== $0"
    echo "   FAIL! (Threads: $FAIL)"
    exit 1
fi

}
