#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

awk '{print $1,$2}' $1/* | sed -e 's: :\n:g'|sort | uniq -c  | sort -n 
