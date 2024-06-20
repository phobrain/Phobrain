#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

set -e

TARGET=$1

SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

PHOB_LOCAL=`"$SCRIPTPATH"/get_phobrain_local.sh`

PROPERTIES="$PHOB_LOCAL"/build.properties

if [ ! -f "$PROPERTIES" ] ; then
    echo == $0 Error: No "$PROPERTIES"
    exit 1
fi

T="^${TARGET}="
G=`grep "$T" "$PROPERTIES"`
echo $G | sed -e "s:^.*=::"
