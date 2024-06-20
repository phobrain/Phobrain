#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#

set -e

IMAGE_DIR=`../../bin/phobrain_property.sh image.dir`

echo "-- $0 - IMAGE_DIR is $IMAGE_DIR"

IMAGE_LIST=./new.list

echo == $0 USING $IMAGE_LIST

echo Orientations:
egrep -v '^#' $IMAGE_LIST | awk 'NF=2 {print $2}' | sort | uniq -c

./run $IMAGE_DIR -f $IMAGE_LIST $*
