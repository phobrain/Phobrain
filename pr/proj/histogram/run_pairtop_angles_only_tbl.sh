#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#

echo "== $0"
echo "   - needs pairs_uniq_[vh] created by proj/pairs/"
echo "== TODO check how old those files are"

set -e

time {

START=`date`
echo Start: $START

if [ $# != 1 ] ; then
	echo "== $0 need v or h"
	exit 1
fi

BULK_HISTOS=`phobrain_property.sh bulk.histogram`

DIST_DEST="$BULK_HISTOS/pair_dist"
ANGLE_DEST="$BULK_HISTOS/pair_angle"

./run_prod_pairs.sh  	-$1 \
        -pairtop pr.pairtop_angle_${1}_dump.sql \
		b10_d_rgb2-rgb_2-angle \
		b11_d_rgb3-rgb_3-angle \
		b12_d_rgb32-rgb_32-angle

# order matters
mv ${ORIENT}_*rgb*_len "$ANGLE_DEST"
mv ${ORIENT}_b* "$DIST_DEST"

echo "=== $0 $1"
echo "    START: " $START
echo "    END:   " `date`

}
