#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#


set -e

ML="`../../bin/phobrain_property.sh ml.h.dir`"

FNAME=$ML/i9_dirPB_5.pb

echo "== $0 using $FNAME"

time java -Xmx40g -jar build/libs/update-all-1.0.jar d0 h $FNAME

