#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#

echo "== $0  - make calcs on pairs of histograms"

set -e

IMAGE_DIR=`../../bin/phobrain_property.sh image.dir`
IMAGE_DESC_DIR=`../../bin/phobrain_property.sh image.desc.dir`

if [ ! -d "$IMAGE_DIR" ] ; then
    echo "== $0  image dir is not a dir: $IMAGE_DIR"
    exit 1
fi

if [ ! -d "$IMAGE_DESC_DIR" ] ; then
    echo "== $0  image_desc dir is not a dir: $IMAGE_DESC_DIR"
    exit 1
fi

IMAGE_HIST_DIR="$IMAGE_DESC_DIR"/2_hist/pics/

if [ ! -d "$IMAGE_HIST_DIR" ] ; then
    echo "== $0  image hist dir is not a dir: $IMAGE_HIST_DIR"
    exit 1
fi

IMAGE_LIST=`../../bin/get_phobrain_image_list.sh`

if [ ! -f "$IMAGE_LIST" ] ; then
    echo "== $0  image list is not a file: $IMAGE_LIST"
    exit 1
fi

ORIENT=$1 ; shift

echo "-- $0 - image hist dir is $IMAGE_HIST_DIR"
echo "-- $0 - image list is $IMAGE_LIST"
echo "-- $0 - orient is $ORIENT"

echo Orientations:
egrep -v '^#' $IMAGE_LIST | awk 'NF=2 {print $2}' | sort | uniq -c

#debug
# CMD="./run $IMAGE_DIR -cache $IMAGE_HIST_DIR $ORIENT -f $IMAGE_LIST $*"
CMD="./run $IMAGE_DIR $ORIENT -f $IMAGE_LIST $*"

echo $CMD
$CMD
