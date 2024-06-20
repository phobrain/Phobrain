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

# run from proj/

IMAGE_DIR=`../bin/phobrain_property.sh image.dir`
IMAGE_DESC_DIR=`../bin/phobrain_property.sh image.desc.dir`

echo "-- $0"
echo "    IMAGE_DIR  is $IMAGE_DIR"
echo "    IMAGE_DESC_DIR is $IMAGE_DESC_DIR"
echo

DIR=$IMAGE_DIR/$1

echo "=== $0"
echo "     Import pr.picture image data for archive $1 from $DIR"
echo "     Assumes image_desc/0_color/$1 has files: sizes density colors"
echo
if [ ! -d $DIR ] ; then
    echo no dir: $DIR
    exit 1
fi

echo "== $0"
echo "    Deleting archive $1 from pr.picture in 5 sec"
sleep 5
psql -d pr -c "delete from pr.picture where archive=$1"

echo "    Deleted archive $1 from pr.picture"
echo

IMPORT="./import"

echo "== $0"
echo "   Compiling in $IMPORT"

cd $IMPORT
gradle fatJar

echo "== $0"
echo "  Running ./run.n $IMAGE_DIR $IMAGE_DESC_DIR $1 in $IMPORT"
echo

./run.n $IMAGE_DIR $IMAGE_DESC_DIR $1

echo "=== $0"
echo "     Import archive $1 done"

