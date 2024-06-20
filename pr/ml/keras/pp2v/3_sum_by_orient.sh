#!/bin/bash
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === 3_sum_by_orient.sh
#
#       Third ML step in adding photos.
#
#       Run java predtool. Predtool sums all .pairs per dir 
#       into java PairsBin .pb files, then adds subdir .pb's 
#       up the tree.
#
#       The top-dir .pb is read by proj/pairs to make the 
#       pr.pairs_[vh]_dump.sql file's pr.pairs_[vh].d0 column,
#       and all .pairs and .pb are read by proj/pairtop, which 
#       turns them into 'tags' in pr.pairtop_nn_[vh]_dump.sql.
#

set -e

if [ $# -gt 1 ] ; then
    echo "usage: $0 [<dir>] $#"
    exit 1
fi

BASE=.

if [ $# == 1 ] ; then
    BASE=$1
fi

# scripts in pr/bin/
PHOB_HOME=`get_phobrain_home.sh`
PHOB_LOCAL=`get_phobrain_local.sh`

COMPILE=$PHOB_HOME/pr_git/pr/proj/predtool

(cd $COMPILE ; gradle fatJar )

EXEC=$COMPILE/run.sh

N_V=`egrep -v "^#" $PHOB_LOCAL/real_img_orient | awk '$2=="t"' | wc -l`

CMD="$EXEC $N_V -sum $BASE"
echo "== $0 running $CMD"

$CMD

echo === $0 $BASE DONE `date`
