#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#

# TODO _ full pairs == whatever needed to run pairs
#      rename?  angle +?

set -e

if [ $# != 1 ] ; then
    echo "Args: $# Usage: $0 <v|h>"
    exit 1
fi

ORIENT=$1

BULK_HISTOS=`../../bin/phobrain_property.sh bulk.histogram`

if [ ! -d "$BULK_HISTOS" ] ; then
    echo "-- $0 No dir: $BULK_HISTOS"
    exit 1
fi

DEST1="$BULK_HISTOS/pair_dist"
DEST2="$BULK_HISTOS/pair_angle"

RM=""

if [ ! -d $DEST1 ] ; then
    echo Creating $DEST1
    mkdir $DEST1
else
    RM="y"
fi

if [ ! -d $DEST2 ] ; then
    echo Creating $DEST2
    mkdir $DEST2
else
    RM="y"
fi

if [ "$RM" == "y" ] ; then
    echo "Removing previous $ORIENT $DEST1 and $DEST2 files in 5.."
    sleep 5
    /bin/rm -f $DEST1/${ORIENT}_b*
    /bin/rm -f $DEST2/${ORIENT}_*rgb*_len
fi

O=""
case "$ORIENT" in 
    [vh])
        echo vh ok ;;
    *)
        echo "Orient: $ORIENT Usage: $0 <v|h>"
        exit 1
        ;;
esac

time {

START=`date`

echo == $0 MASTER $ORIENT TBL BUILD rename ang, and move to $DEST1 and $DEST2
echo Some _dist files with distributions will remain here. Do not examine.
echo You made this decision, not me, at $START

echo "== $0 removing ${ORIENT}_*"

/bin/rm -f ${ORIENT}_*

./run_pairtop_angles_only_tbl.sh $ORIENT

#./run_pairs_tbl.sh "-"$ORIENT

if [ $? == "0" ] ; then
    echo ran ok
else
    echo Rats
    date
    exit 1
fi

echo moving $ORIENT _rgb_ _len files to $DEST2
ls ${ORIENT}_*rgb*_len $DEST2
mv ${ORIENT}_*rgb*_len $DEST2
ls -ltr $DEST2

echo moving ${ORIENT} _bNs to $DEST1
ls ${ORIENT}_b*
mv ${ORIENT}_b* $DEST1
ls -ltr $DEST1

echo Whole shebang:
echo $START
date

}
