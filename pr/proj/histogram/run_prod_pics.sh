#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#

echo "== $0  - make histograms from images"

set -e

IMAGE_LIST=`../../bin/get_phobrain_image_list.sh`

IMAGE_DIR=`../../bin/phobrain_property.sh image.dir`

echo "-- $0 - image dir is $IMAGE_DIR image list is $IMAGE_LIST"

echo Orientations:
egrep -v '^#' $IMAGE_LIST | awk 'NF=2 {print $2}' | sort | uniq -c

CMD="./run $IMAGE_DIR -f $IMAGE_LIST $*"
echo "== $0  Running: $CMD"

./run $IMAGE_DIR -f $IMAGE_LIST $*

