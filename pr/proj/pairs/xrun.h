#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

if [ $# -gt 0 ] ; then
    echo "please dude - no args!"
    exit 1
fi

set -e

PHOB_LOCAL=`../../bin/get_phobrain_local.sh`
IMAGE_DESC_DIR=`"$PHOB_LOCAL"/image_desc_dir.sh`
ML=`"$PHOB_LOCAL"/ml_h_dir.sh`

echo "=== $0 IMAGE_DESC_DIR = $IMAGE_DESC_DIR  ML = $ML"

HPU="../histogram/pairs_uniq_h"

if [ -f pr.pairs_h_dump.sql_body ] || \
   [ -f $HPU ] ; then

	echo "Output file(s) (pr.pairs_h_dump.sql_\*, $HPU) exist - sleeping 5, then removing"
	sleep 5
	rm -f pr.pairs_h_dump.sql_* $HPU
fi

echo Horiz
./run $IMAGE_DESC_DIR -all \
	avg d0 \
        $ML

# 1 2 3 4 5 7 8 9 10 11 12 13 14 23 33 34 35 36 37 38 \

ls -l pr.pairs_h_dump* $HPU
du -sh pr.pairs_h_dump* $HPU
