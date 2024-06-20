#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === 1_mk_arch.sh - Add/update records in pr.picture,
#                    leaving per-pic info ready for pair calculations.
#
#           Decide orientation for real_image_orient list.
#           Calc per-pic:
#                aggregate color info, 
#                color histograms,
#                imagenet vecs.
#           Import pics to db with histograms and imagenet vecs.
#
#           If there are user-data-trained pair models, or user data to train:
#               Build pickle/dill file for training/predictions,
#                       from imagenet vecs and histograms.
#               If there are trained pair models,
#                   Predict .vecs files and load them to pr.picture
#                   with update. 
#                   TODO - predict/update .vecs for just the archives specified?

##   arg(s) == id#s of archives being added/modified

if [ $# == 0 ]; then
    echo "Usage: $0 <archnum1> <arch2> ..."
    exit 1
fi

set -e

SCRIPT_DIR=./1_mk_arch_scripts

START=`date`

echo "=== $0"
echo "    Making new real_img_orient for all args==archives"

ARGS="$*"

update_real_img_orient_from_dirs.sh $*

CMDS="s1_make_md.sh s2_mlhists.sh s3_mljpegs.sh s4_import_db.sh"

echo "=== $0"
echo "    Running per-archive metadata and db import scripts:"
echo "      $CMDS"

while [ $# != 0 ]; do

    echo "=== $0"
    echo "    Archive: $1"
    echo "      $CMDS"
 
    for cmd in $CMDS; do

        CMD="$SCRIPT_DIR/$cmd $1"

        echo "== $0 running $CMD"
        $CMD

    done

    echo === $0 archive setup done for: $1

    shift
done

echo

echo "=== $0"
echo "    Predicting/loading any vectors from user-labeled pairs"

$SCRIPT_DIR/s5_ml_pair_vecs.sh $ARGS

echo "=== $0  DONE"
echo "START $START"
echo "DONE  `date`"
echo
if [ -x "$(command -v funwait.sh)" ] ; then
    funwait.sh
fi
