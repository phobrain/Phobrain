#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

PHOB_LOCAL=`"$SCRIPTPATH"/get_phobrain_local.sh`

if [ ! -f "$PHOB_LOCAL"/real_img_orient ] ; then
    echo == $0 Error: No "$PHOB_LOCAL"/real_img_orient
    exit 1
fi

echo "$PHOB_LOCAL"/real_img_orient

