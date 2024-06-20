#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === make_real_img_orient_from_db.sh
#
# Also see update_real_img_orient_from_files.sh
#

#set -e

if [ $# != 0 ] ; then
    echo "== $0"
    echo "   Usage: no args"
    exit 1
fi

DIR=`get_phobrain_local.sh`

OUT_FILE=$DIR/real_img_orient
PREV_OUT=

echo "=== $0"
echo "    Generate $OUT_FILE"

if [ -e $OUT_FILE ] ; then
    PREV_OUT="${OUT_FILE}."`date +%s`
    echo "    Moving prev to $PREV_OUT"
    mv $OUT_FILE $PREV_OUT
fi

# ------

MSG1="# Running in: `pwd`"
MSG2="# \t$0"
DATE=`date`

echo $MSG1 > $OUT_FILE
echo -e $MSG2     >> $OUT_FILE
echo "# $DATE" >> $OUT_FILE
echo "# pic   vertical" >> $OUT_FILE

psql -d pr --pset pager=off -c  \
    "select archive, file_name, vertical, length(file_name) as flen from pr.picture order by archive, sequence, flen, file_name ;" \
    | egrep -v archive \
    | awk 'NF==7 {print $1"/"$3" "$5}' \
    | sort -V \
    >> $OUT_FILE

#   | sed -e 's:|:/:g' | sed -e 's: ::g' | sed -e 's:/t: t:g' -e 's:/f: f:g' \
#   | awk 'NF>0' \

echo "== $0"
echo "   Made $OUT_FILE"
echo "   pics: `egrep -v '^#' $OUT_FILE | wc -l`"
echo "      V: `egrep -v '^#' $OUT_FILE | awk '$2 == "t"' | wc -l`"
echo "      H: `egrep -v '^#' $OUT_FILE | awk '$2 == "f"' | wc -l`"

if [ -e $PREV_OUT ] ; then
    echo "   Previous: $PREV_OUT"
    echo "   Diff line counts:"
    diff "$PREV_OUT" "$OUT_FILE" > /tmp/diff.$$
    echo "       <: `egrep '^<' /tmp/diff.$$ | wc -l`"
    echo "       ---: `egrep '^---' /tmp/diff.$$ | wc -l`"
    echo "       >: `egrep '^>' /tmp/diff.$$ | wc -l`"
    read -r -p "   View? [Y/n]? " response
    if [[ "$response" != "n" && "$response" != "N" ]] ; then
        diff $PREV_OUT $OUT_FILE
    fi
fi
echo
