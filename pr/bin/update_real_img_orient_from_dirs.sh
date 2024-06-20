#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

#
# === make_real_img_orient_from_dirs.sh
#
#     Create master list for photos, replacing or
#     adding from images/<archive> dirs in args.
#     This is for importing new photos.
#
#     Also see make_real_img_orient_from_db.sh
#

set -e

# == args are id#s of archive

if [ $# == 0 ] ; then
    echo "=== Usage:"
    echo "    $0  archive1 a2 a3.."
    exit 1
fi

PHOB_LOCAL=`get_phobrain_local.sh`

IMG_LIST="$PHOB_LOCAL"/real_img_orient

IMAGE_DIR=`phobrain_property.sh image.dir`

PROJ_DIR=`phobrain_property.sh proj.dir`

TMP="/tmp/real_img_orient"

echo "-- $0 IMAGES $IMAGE_DIR"

START=`date`

MSG1="# Running in: `pwd`"
MSG2="# \t$0"
DATE=`date`

T=/tmp/xx.$$
touch $T

EGREP_V="^#"

function progress() {

    sleep 3
    echo "5-sec progress on $1"
    while true; do
        if [ -f $1 ] ; then
            N=`cat $1|wc -l`
            local l=`tail -1 $1`
            echo -ne "$N  $l    \r"
        fi
        sleep 5
    done
}

#progress $T &
#whilePID=$!

echo "-- $0 argcheck"

ARGS="$*"

while(($#)) ; do

    if (($1 <= 0)) ; then
        echo "Usage: $0 <archnum>"
        echo "       archnum is an int"
        exit 1
    fi

    IDIR=$IMAGE_DIR/$1

    if [ ! -d $IDIR ] ; then
        echo "=== Error: no pic dir: $IDIR"
        exit 1
    fi

    N=`ls -1 $IDIR | grep -i jpg | wc -l`

    if [ $N == "0" ] ; then
        echo "== $0  No jpgs in $IDIR"
        exit 1
    fi

    echo "--  $IDIR jpgs $N"

    EGREP_V=$EGREP_V"|^$1/"
    shift

done

echo "== $0  args ok:  Making new list for these archives in $T"

(cd $PROJ_DIR/import ; gradle fatJar ; ./run.orientlist $T "$IMAGE_DIR" "$ARGS")

#kill $whilePID

echo "-- $0 stripping [$EGREP_V] from $IMG_LIST onto the end of $T"

egrep -v "$EGREP_V" $IMG_LIST >> $T

echo "-- $0"
echo "   sort -V $T > /tmp/img_list_only"

sort -V $T > /tmp/img_list_only

egrep -v '^#' $IMG_LIST | sort -V > $T

echo "== diff $IMG_LIST and $TMP ?"
echo "   via (sorted -V)"
echo "  $ diff $T /tmp/img_list_only | less"
echo -n "(y/N): "
read X

if [[ "$X" == "y" || "$X" == "Y" ]] ; then

    diff $T /tmp/img_list_only | less

    echo "-- $0"
    echo "    =>  did diff $T /tmp/img_list_only"
fi

echo Lines:
wc -l $IMG_LIST $T

echo "   => finally, make $TMP"

echo $MSG1      > $TMP
echo -e $MSG2   >> $TMP
echo "# $DATE"  >> $TMP
echo "# pic   vertical" >> $TMP

sort -V /tmp/img_list_only >> $TMP

#rm $T
echo Old header:
grep '^#' $IMG_LIST
echo
echo New header:
grep '^#' $TMP
echo

echo -n "  Replace $IMG_LIST with a copy of $TMP ? (Y/n): "
read X

if [ "$X" == "n" ] && [ "$X" == "N" ] ; then
    echo "== $0 Exiting with error to stop build, leaving $TMP"
    exit 1
fi

OLD=${IMG_LIST}.`date +%s`
echo "Moving $IMG_LIST to $OLD"
mv "$IMG_LIST" "$OLD"
echo "Copying $TMP to $IMG_LIST"
cp -p $TMP $IMG_LIST

echo "== $0"
echo "   Made $IMG_LIST"
echo "   pics: `egrep -v '^#' $IMG_LIST | wc -l`"
echo "      V: `egrep -v '^#' $IMG_LIST | awk '$2 == "t"' | wc -l`"
echo "      H: `egrep -v '^#' $IMG_LIST | awk '$2 == "f"' | wc -l`"

echo "=== $0 DONE"
echo "START $START"
echo "DONE  `date`"
