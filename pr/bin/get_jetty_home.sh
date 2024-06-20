#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

set -e

SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

PHOB_LOCAL=`"$SCRIPTPATH"/get_phobrain_local.sh`
PHOB_HOME=`"$SCRIPTPATH"/get_phobrain_home.sh`

PROPERTIES="$PHOB_LOCAL"/build.properties

if [ ! -f "$PROPERTIES" ] ; then
    echo == $0 Error: No "$PROPERTIES"
    exit 1
fi

VERSION_DIR=`grep '^jetty.home=' "$PROPERTIES" | sed -e 's:^.*=::'`

echo "$PHOB_HOME"/"$VERSION_DIR"
