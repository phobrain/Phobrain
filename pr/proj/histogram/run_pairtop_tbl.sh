#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#


set -e

time {

START=`date`
echo Start: $START

if [ $# != 1 ] ; then
	echo "== $0 need v or h"
	exit 1
fi

./run_prod_pairs.sh  	-$1 \
        -pairtop pr.pairtop_col_${1}_dump.sql \
		b4_grey-gs_128-dist  \
		b5_hs24-hs_24-dist \
		b6_hs48-hs_48-dist \
		b7_rgb12-rgb_12-dist \
		b8_rgb24-rgb_24-dist \
		b9_rgb32-rgb_32-dist \
		b10_d_rgb2-rgb_2-angle \
		b11_d_rgb3-rgb_3-angle \
		b12_d_rgb32-rgb_32-angle

echo "START: " $START
echo "END:   " `date`

}
