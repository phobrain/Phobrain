#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#

# normally called by ../1_mk_arch_scripts/s2_mlhists.sh 

OUT_DIR=hists_concat

set -e

if [ -e "$OUT_DIR" ] ; then

    echo "=== $0  OUT_DIR $OUT_DIR exists - contents:"

    ls "$OUT_DIR"
    du "$OUT_DIR"
    echo "=== $0  Removing $OUT_DIR in 5 sec"
    sleep 5
    /bin/rm -rf "$OUT_DIR"
fi

START=`date`

# This does new_list; to do all archives in one go, run_ml_hist_all.sh

./run_new_list.sh -by_archive -concat	\
                "${OUT_DIR}"-gs_128-hist  \
                "${OUT_DIR}"-s_128-hist \
                "${OUT_DIR}"-rgb_12-hist

echo "== $0  DONE - result in $OUT_DIR"

echo "START: " $START
echo "END:   " `date`

