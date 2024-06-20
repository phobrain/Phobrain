#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#

set -e

OUT_DIR=ml_concat
 
IMAGE_DESC=`../../bin/phobrain_property.sh image.desc.dir`
HIST_DEST="$IMAGE_DESC"/2_hist/pics/ml_concat

echo "== $0  local out_dir: $OUT_DIR   dest dir: $HIST_DEST"

if [ ! -d "$HIST_DEST" ] ; then
    echo "== $0  making dest dir: $HIST_DEST in 5.."
    sleep 5
    mkdir -P $HIST_DEST
else
    N=`ls -1 "$HIST_DEST" | egrep '^[1-9]' | wc -l`
    SZ=`/usr/bin/du -sh -d 0 "$HIST_DEST" | awk '{print $1}'`
    echo "== $0  Removing $N archive dirs ($SZ) in "$HIST_DEST" in 5.."
    sleep 5
    /bin/rm -rf "$HIST_DEST/*"
fi

START=`date`

./run_prod_pics.sh -by_archive -concat	\
		${OUT_DIR}-gs_128-hist  \
		${OUT_DIR}-s_128-hist \
		${OUT_DIR}-rgb_12-hist

echo "== $0   DONE - results in $OUT_DIR"

echo "== $0  moving to $HIST_DEST in 5"
sleep 5

\ls -1 -d $OUT_DIR/* | while read i ; do

    # might take time so be wordy
    echo "== moving archive $i  `du -sh $i` to $HIST_DEST"

    old=$HIST_DEST/`basename $i`
    echo == rm $old
    /bin/rm -rf $old

    echo == mv $i
    mv $i $HIST_DEST/
done

echo "START: " $START
echo "END:   " `date`

