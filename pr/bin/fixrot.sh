#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

echo $1
convert -auto-orient $1 /tmp/$$.jpg

mv /tmp/$$.jpg $1
