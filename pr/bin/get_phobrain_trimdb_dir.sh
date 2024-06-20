#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

PHOB_LOCAL=`"$SCRIPTPATH"/get_phobrain_local.sh`

PROPERTIES="$PHOB_LOCAL"/build.properties

if [ ! -f "$PROPERTIES" ] ; then
    echo == $0 Error: No "$PROPERTIES"
    exit 1
fi

grep '^trimdb.dir=' "$PROPERTIES" | sed -e 's:^.*=::'

