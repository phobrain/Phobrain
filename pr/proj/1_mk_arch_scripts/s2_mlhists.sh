#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

set -e

# == arg is id# of archive

if [ $# != 1 ]; then
    echo "Usage: $0 <archnum>"
    exit 1
fi
pwd


# run from proj/
IMAGE_DIR=`../bin/phobrain_property.sh image.dir`
IMAGE_DESC=`../bin/phobrain_property.sh image.desc.dir`
IMAGE_LIST=`../bin/get_phobrain_image_list.sh`

if [ ! -d "$IMAGE_DIR" ] ; then
    echo "== $0 Error: No image.dir [$IMAGE_DIR]"
    exit 1
fi

if [ ! -d "$IMAGE_DESC" ] ; then
    echo "== $0 Error: No image.desc.dir [$IMAGE_DESC]"
    exit 1
fi

HIST_DEST="$IMAGE_DESC"/2_hist/pics/ml_concat

if [ ! -d "$HIST_DEST" ] ; then
    echo "== $0 Error: No hist dest [$HIST_DEST]"
    exit 1
fi

if [ ! -f "$IMAGE_LIST" ] ; then
    echo "== $0 Error: No $IMAGE_LIST"
    exit 1
fi

echo "-- $0 - image list is $IMAGE_LIST destination is $HIST_DEST"

ls -l "$IMAGE_LIST"

cd histogram

gradle fatJar

echo === $0 Making hists for ML, archive dir $1 real_img_orient $IMAGE_LIST

rm -f new.list

CMD="egrep ^$1/ $IMAGE_LIST"
echo == $0 Running $CMD
$CMD > new.list

echo === $0 NEW pics for hists: 
wc -l new.list

SCRIPT=./run_hist_new_list.sh
OUT_DIR=`grep 'OUT_DIR=' $SCRIPT | sed -e 's:^OUT_DIR=::g'`

echo === $0 Run $SCRIPT OUT_DIR is $OUT_DIR

$SCRIPT

echo "=== $0 Move hists from $OUT_DIR to dests (in $HIST_DEST from wd=`pwd`)"

\ls -d $OUT_DIR/* | while read i ; do
    echo "== Moving `du -sh $i | awk '{print $2,$1}'`"
    dname=$(basename $i)
    echo "-- Install as: $HIST_DEST/$dname"
    rm -rf "$HIST_DEST/$dname"
    mv $i $HIST_DEST/
done

echo "=== $0 DONE `date`"
