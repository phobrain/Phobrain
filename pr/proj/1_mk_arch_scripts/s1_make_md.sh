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

if (($1 > 0)) ; then
    echo "=== $0 $1  IMAGES $IMAGE_DIR"
else
    echo "Usage: $0 <archnum>"
    echo "       archnum is an int"
    exit 1
fi

START=`date`

IDIR=$IMAGE_DIR/$1

#LOG=/tmp/`basename $0`.$$.log
#echo lotsa-file LOG is $LOG
#touch $LOG

echo "=== $0 Prepare $DDIR files with data from pics in $IDIR "
echo "===     for import into database"

if [ ! -d $IDIR ] ; then
    echo "=== Error: no pic dir: $IDIR"
    exit 1
fi

echo Starting in 5 sec
sleep 5

COLPIC="./colpic"

echo "=== $0 Calc colors in $COLPIC"

cd $COLPIC

gradle fatJar

echo "=== $0 running ./run.n $1 in $COLPIC"

./run.n $1

echo "=== $0 done: run.n $1 in $COLPIC"

echo "=== $0 archive $1 DONE"
echo "START $START"
echo "DONE  `date`"
