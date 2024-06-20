#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#


set -e

IMAGE_DIR=`../../bin/phobrain_property.sh image.dir`

echo "" > xl

cat $1 | while read i ; do 
    echo $i `../../bin/isvert.sh $IMAGE_DIR/$i | awk '{print $2}'` >> xl
done

echo result is xl
