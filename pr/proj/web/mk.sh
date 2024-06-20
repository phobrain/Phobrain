#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

gradle assemble

if [ $? -eq 0 ] ; then
    CMD="cp build/libs/pr.war ../../jetty-base/webapps"
    echo $CMD
    $CMD
fi
